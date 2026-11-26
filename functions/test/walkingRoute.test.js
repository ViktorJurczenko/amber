// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
const assert = require("node:assert/strict");
const test = require("node:test");
const {callableModule, resetState, state, storedFirestore} = require("./support/fakes");
const {expectHttpsError} = require("./support/assertions");

/**
 * The route the function returns, as the app reads it. The app's FirebaseRouteRepositoryTest reads
 * the same file, so both sides agree on the format.
 */
const ROUTE = require("./fixtures/walkingRoute.json");

/** Lausanne Flon and the cathedral, a short walk apart: where ROUTE starts and ends. */
const ORIGIN = ROUTE.steps[0].start;
const DESTINATION = ROUTE.steps.at(-1).end;
const REQUEST = {origin: ORIGIN, destination: DESTINATION};
const SIGNED_IN = {uid: "patient-1"};

const MAX_LATITUDE = 90;
const MAX_LONGITUDE = 180;

/** How long the app waits for the function: CALL_TIMEOUT_SECONDS in FirebaseRouteRepository. */
const APP_CALL_TIMEOUT_MS = 20_000;

/** The fields the app reads. Any other one would cost more; a missing one would break guidance. */
const FIELD_MASK = [
  "routes.distanceMeters",
  "routes.duration",
  "routes.polyline.geoJsonLinestring",
  "routes.legs.steps.distanceMeters",
  "routes.legs.steps.navigationInstruction.maneuver",
  "routes.legs.steps.startLocation",
  "routes.legs.steps.endLocation",
];

/** A point as the Routes API writes it. */
function latLng({lat, lng}) {
  return {latLng: {latitude: lat, longitude: lng}};
}

/** A step of ROUTE as the Routes API writes it. */
function apiStep({maneuver, distanceMeters, start, end}) {
  return {
    distanceMeters,
    navigationInstruction: {maneuver},
    startLocation: latLng(start),
    endLocation: latLng(end),
  };
}

/** The line of ROUTE as the Routes API writes it: GeoJSON, so [lng, lat] pairs. */
const LINE = {geoJsonLinestring: {
  type: "LineString",
  coordinates: ROUTE.points.map(({lat, lng}) => [lng, lat]),
}};

/**
 * The Routes API's answer for ROUTE, as the field mask asks for it: one leg per step, so the test
 * also checks that the steps of every leg are joined in order.
 */
function routesAnswer(steps = ROUTE.steps.map(apiStep)) {
  return {
    routes: [{
      distanceMeters: ROUTE.distanceMeters,
      duration: `${ROUTE.durationSeconds}s`,
      polyline: LINE,
      legs: steps.map((step) => ({steps: [step]})),
    }],
  };
}

/**
 * Makes the Routes API answer with status and body, or throw when answer is an Error, until test t
 * ends. The calls of the returned mock are the requests sent to it.
 */
function routesApiAnswers(t, answer, status = 200) {
  return t.mock.method(globalThis, "fetch", async () => {
    if (answer instanceof Error) throw answer;
    return {ok: status >= 200 && status < 300, status, json: async () => answer};
  });
}

/**
 * The function, with an in-memory Firestore for the counts of calls. Each top-level test starts
 * with an empty one, through resetState.
 */
function walkingRoute() {
  state.store ??= new Map();
  return callableModule({firestore: storedFirestore()}).getWalkingRoute;
}

/** The request of the call number index to a mock made by routesApiAnswers. */
function sentRequest(fetch, index = 0) {
  const [url, init] = fetch.mock.calls[index].arguments;
  return {url, init};
}

test("rejects signed-out and malformed requests without calling the Routes API", async (t) => {
  resetState();
  const fetch = routesApiAnswers(t, routesAnswer());
  const getWalkingRoute = walkingRoute();
  await t.test("requires sign-in", async () => {
    await expectHttpsError(
      getWalkingRoute({data: REQUEST}),
      "unauthenticated",
      "Sign in before asking for a route",
    );
  });

  const cases = [
    ["no data", undefined],
    ["no destination", {origin: ORIGIN}],
    ["an extra field", {...REQUEST, mode: "DRIVE"}],
    ["a point with an extra field", {origin: {...ORIGIN, alt: 0}, destination: DESTINATION}],
    ["a latitude above the largest", {origin: {...ORIGIN, lat: MAX_LATITUDE + 0.5}, destination: DESTINATION}],
    ["a latitude below the smallest", {origin: {...ORIGIN, lat: -MAX_LATITUDE - 0.5}, destination: DESTINATION}],
    ["a longitude above the largest", {origin: ORIGIN, destination: {...DESTINATION, lng: MAX_LONGITUDE + 1}}],
    ["a longitude below the smallest", {origin: ORIGIN, destination: {...DESTINATION, lng: -MAX_LONGITUDE - 1}}],
    ["a coordinate as text", {origin: {...ORIGIN, lat: String(ORIGIN.lat)}, destination: DESTINATION}],
    ["a coordinate that is not a number", {origin: {...ORIGIN, lng: Number.NaN}, destination: DESTINATION}],
    ["a point as a list", {origin: [ORIGIN.lat, ORIGIN.lng], destination: DESTINATION}],
  ];
  for (const [name, data] of cases) {
    await t.test(`rejects ${name}`, async () => {
      await expectHttpsError(
        getWalkingRoute({auth: SIGNED_IN, data}),
        "invalid-argument",
        "A valid origin and destination are required",
      );
    });
  }
  assert.equal(fetch.mock.callCount(), 0);
  // a refused request costs nothing, so it isn't counted
  assert.deepEqual([...state.store.keys()], []);

  await t.test("accepts points exactly on the limits", async () => {
    const onTheLimits = {
      origin: {lat: MAX_LATITUDE, lng: -MAX_LONGITUDE},
      destination: {lat: -MAX_LATITUDE, lng: MAX_LONGITUDE},
    };
    await getWalkingRoute({auth: SIGNED_IN, data: onTheLimits});
    assert.equal(fetch.mock.callCount(), 1);
  });
});

test("asks for a walking route with the server's key and returns it as the app reads it", async (t) => {
  resetState();
  const fetch = routesApiAnswers(t, routesAnswer());
  // a spy: the real timeout still runs
  const timeout = t.mock.method(AbortSignal, "timeout");
  const getWalkingRoute = walkingRoute();

  const route = await getWalkingRoute({auth: SIGNED_IN, data: REQUEST});

  const {url, init} = sentRequest(fetch);
  assert.equal(url, "https://routes.googleapis.com/directions/v2:computeRoutes");
  const {"X-Goog-FieldMask": mask, ...headers} = init.headers;
  assert.deepEqual(headers, {"Content-Type": "application/json", "X-Goog-Api-Key": "test-routes-key"});
  assert.deepEqual(mask.split(",").sort(), [...FIELD_MASK].sort());
  // Without GeoJSON, Google would send an encoded line, and every route would be "no route"
  assert.deepEqual(JSON.parse(init.body), {
    origin: {location: latLng(ORIGIN)},
    destination: {location: latLng(DESTINATION)},
    travelMode: "WALK",
    polylineEncoding: "GEO_JSON_LINESTRING",
    units: "METRIC",
  });
  // gives up before the app does, so the app can still use the kept route
  assert.equal(timeout.mock.callCount(), 1);
  const [waitMs] = timeout.mock.calls[0].arguments;
  assert.ok(waitMs > 0 && waitMs < APP_CALL_TIMEOUT_MS, `waits ${waitMs} ms`);
  assert.equal(init.signal, timeout.mock.calls[0].result);
  // GeoJSON's [lng, lat] turned into {lat, lng}, and the steps of every leg in order
  assert.deepEqual(route, ROUTE);
});

test("reads the zero values the Routes API leaves out, and refuses a step without a location", async (t) => {
  resetState();
  const [first, last] = ROUTE.steps;
  await t.test("a route without distance, duration or legs", async (t) => {
    routesApiAnswers(t, {routes: [{polyline: LINE}]});
    const getWalkingRoute = walkingRoute();

    const route = await getWalkingRoute({auth: SIGNED_IN, data: REQUEST});

    // no steps is fine: a very short walk can have none, and the line can still be drawn
    assert.deepEqual(route, {distanceMeters: 0, durationSeconds: 0, points: ROUTE.points, steps: []});
  });
  await t.test("a leg without steps", async (t) => {
    const [answer] = routesAnswer().routes;
    routesApiAnswers(t, {routes: [{...answer, legs: [{}, ...answer.legs]}]});
    const getWalkingRoute = walkingRoute();

    const route = await getWalkingRoute({auth: SIGNED_IN, data: REQUEST});

    assert.deepEqual(route.steps, ROUTE.steps);
  });
  await t.test("no instruction, no distance, a latitude of 0", async (t) => {
    const onTheEquator = {...last.end, lat: 0};
    routesApiAnswers(t, routesAnswer([
      apiStep(first),
      {startLocation: latLng(last.start), endLocation: {latLng: {longitude: onTheEquator.lng}}},
    ]));
    const getWalkingRoute = walkingRoute();

    const route = await getWalkingRoute({auth: SIGNED_IN, data: REQUEST});

    assert.deepEqual(route.steps.at(-1), {
      maneuver: "MANEUVER_UNSPECIFIED", distanceMeters: 0, start: last.start, end: onTheEquator,
    });
  });
  await t.test("a step without a location", async (t) => {
    const {endLocation, ...noEnd} = apiStep(last);
    routesApiAnswers(t, routesAnswer([apiStep(first), noEnd]));
    const getWalkingRoute = walkingRoute();
    await expectHttpsError(
      getWalkingRoute({auth: SIGNED_IN, data: REQUEST}),
      "internal",
      "The route service gave an incomplete route",
    );
  });
});

test("says when there is no walking route", async (t) => {
  resetState();
  const answers = [
    ["no routes", {}],
    ["an empty list", {routes: []}],
    ["a route without a line", {routes: [{distanceMeters: ROUTE.distanceMeters}]}],
  ];
  for (const [name, answer] of answers) {
    await t.test(name, async (t) => {
      routesApiAnswers(t, answer);
      const getWalkingRoute = walkingRoute();
      await expectHttpsError(
        getWalkingRoute({auth: SIGNED_IN, data: REQUEST}),
        "not-found",
        "No walking route was found",
        "NO_ROUTE",
      );
    });
  }
});

test("reports the Routes API failing as unavailable, without logging the points", async (t) => {
  resetState();
  const consoleError = t.mock.method(console, "error", () => {});
  await t.test("unreachable or too slow", async (t) => {
    routesApiAnswers(t, new DOMException("The operation was aborted due to timeout", "TimeoutError"));
    const getWalkingRoute = walkingRoute();
    await expectHttpsError(
      getWalkingRoute({auth: SIGNED_IN, data: REQUEST}),
      "unavailable",
      "The route service could not be reached",
    );
  });
  await t.test("refusing, with a message that repeats the request", async (t) => {
    const message = `Invalid origin ${ORIGIN.lat},${ORIGIN.lng} to ${DESTINATION.lat},${DESTINATION.lng}`;
    routesApiAnswers(t, {error: {status: "INVALID_ARGUMENT", message}}, 400);
    const getWalkingRoute = walkingRoute();
    await expectHttpsError(
      getWalkingRoute({auth: SIGNED_IN, data: REQUEST}),
      "unavailable",
      "The route service could not answer",
    );
  });

  const logs = consoleError.mock.calls
    .map((call) => call.arguments.map(String).join(" "))
    .join("\n");
  assert.match(logs, /400 INVALID_ARGUMENT/);
  for (const coordinate of [ORIGIN.lat, ORIGIN.lng, DESTINATION.lat, DESTINATION.lng]) {
    assert.equal(logs.includes(String(coordinate)), false, logs);
  }
});

test("refuses a user's calls past the daily limit, without calling the Routes API", async (t) => {
  resetState();
  t.mock.timers.enable({apis: ["Date"], now: new Date("2026-11-25T10:00:00Z")});
  const fetch = routesApiAnswers(t, routesAnswer());
  const getWalkingRoute = walkingRoute();
  // the module walkingRoute() just loaded with the fakes
  const {DAILY_ROUTE_LIMIT, routeCallsPath} = require("../src/walkingRoute");
  const ask = (uid) => getWalkingRoute({auth: {uid}, data: REQUEST});
  const calls = (uid) => state.store.get(routeCallsPath(uid));
  // yesterday's calls don't count today
  state.store.set(routeCallsPath("walker"), {day: "2026-11-24", count: DAILY_ROUTE_LIMIT});

  for (let call = 0; call < DAILY_ROUTE_LIMIT; call++) await ask("walker");
  assert.deepEqual(calls("walker"), {day: "2026-11-25", count: DAILY_ROUTE_LIMIT});
  await expectHttpsError(ask("walker"), "resource-exhausted", "Too many routes asked for today", "DAILY_LIMIT");
  // refused again, still without asking Google
  await expectHttpsError(ask("walker"), "resource-exhausted", "Too many routes asked for today", "DAILY_LIMIT");
  assert.equal(fetch.mock.callCount(), DAILY_ROUTE_LIMIT);

  // another user has their own count
  await ask("other");
  assert.deepEqual(calls("other"), {day: "2026-11-25", count: 1});

  // the next day, the count starts again
  t.mock.timers.setTime(new Date("2026-11-26T00:00:00Z").getTime());
  await ask("walker");
  assert.deepEqual(calls("walker"), {day: "2026-11-26", count: 1});
});

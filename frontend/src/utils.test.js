import { describe, expect, it } from "vitest";
import { CSV_TEMPLATE, escapeHtml, isTerminalStop, markerClassForStatus, parsePolyline, toDateTimeLocal } from "./utils.js";

describe("parsePolyline", () => {
  it("parses the backend lat,lng;lat,lng format", () => {
    expect(parsePolyline("19.076000,72.877700;19.075960,72.877671")).toEqual([
      [19.076, 72.8777],
      [19.07596, 72.877671],
    ]);
  });

  it("returns [] for empty input and skips malformed pairs", () => {
    expect(parsePolyline("")).toEqual([]);
    expect(parsePolyline(null)).toEqual([]);
    expect(parsePolyline("19.0,72.8;oops;19.1,72.9")).toEqual([
      [19.0, 72.8],
      [19.1, 72.9],
    ]);
  });
});

describe("escapeHtml", () => {
  it("neutralises markup so order text cannot inject HTML into map popups", () => {
    expect(escapeHtml('<img src=x onerror="alert(1)">')).toBe("&lt;img src=x onerror=&quot;alert(1)&quot;&gt;");
    expect(escapeHtml("Tom & Jerry's")).toBe("Tom &amp; Jerry&#39;s");
    expect(escapeHtml(null)).toBe("");
  });
});

describe("status helpers", () => {
  it("knows which stop statuses are final", () => {
    expect(["DELIVERED", "FAILED", "SKIPPED"].every(isTerminalStop)).toBe(true);
    expect(["PENDING", "EN_ROUTE", "ARRIVED"].some(isTerminalStop)).toBe(false);
  });

  it("maps lifecycle status to a marker class", () => {
    expect(markerClassForStatus("DELIVERED")).toContain("delivered");
    expect(markerClassForStatus("FAILED")).toContain("failed");
    expect(markerClassForStatus("ARRIVED")).toContain("active");
    expect(markerClassForStatus("PENDING")).toContain("pending");
  });
});

describe("toDateTimeLocal", () => {
  it("round-trips through a datetime-local input value", () => {
    const iso = "2026-09-21T05:30:00.000Z";
    expect(new Date(toDateTimeLocal(iso)).toISOString()).toBe(iso);
    expect(toDateTimeLocal(null)).toBe("");
  });
});

describe("CSV_TEMPLATE", () => {
  it("has the header columns the backend importer expects", () => {
    expect(CSV_TEMPLATE.split("\n")[0]).toBe(
      "ref,addressText,lat,lng,timeWindowStart,timeWindowEnd,load,priority,notes,customerName,customerPhone"
    );
  });
});

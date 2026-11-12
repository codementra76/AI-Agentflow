/**
 * Utility functions for schedule calculations, IANA timezones, and timestamp formatting.
 */

export const POPULAR_TIMEZONES = [
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Toronto',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Asia/Kolkata',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Asia/Singapore',
  'Asia/Dubai',
  'Australia/Sydney',
  'Pacific/Auckland',
];

export const WEEKDAYS = [
  { value: 'MONDAY', shortLabel: 'Mon', fullLabel: 'Monday' },
  { value: 'TUESDAY', shortLabel: 'Tue', fullLabel: 'Tuesday' },
  { value: 'WEDNESDAY', shortLabel: 'Wed', fullLabel: 'Wednesday' },
  { value: 'THURSDAY', shortLabel: 'Thu', fullLabel: 'Thursday' },
  { value: 'FRIDAY', shortLabel: 'Fri', fullLabel: 'Friday' },
  { value: 'SATURDAY', shortLabel: 'Sat', fullLabel: 'Saturday' },
  { value: 'SUNDAY', shortLabel: 'Sun', fullLabel: 'Sunday' },
];

/**
 * Gets user's local IANA time zone or falls back to 'UTC'
 */
export function getDefaultTimeZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}

/**
 * Converts a date (YYYY-MM-DD) and time (HH:mm) in a specified IANA time zone
 * into a UTC ISO-8601 string (e.g., '2026-11-01T15:00:00.000Z')
 */
export function localDateTimeToUtcInstant(dateStr, timeStr, timeZone) {
  if (!dateStr || !timeStr) return null;
  const [y, m, d] = dateStr.split('-').map(Number);
  const [hh, mm, ss = 0] = timeStr.split(':').map(Number);

  let targetUtc = Date.UTC(y, m - 1, d, hh, mm, ss);
  for (let i = 0; i < 3; i++) {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: timeZone || 'UTC',
      year: 'numeric',
      month: 'numeric',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      second: 'numeric',
      hour12: false,
    }).formatToParts(new Date(targetUtc));

    const map = {};
    parts.forEach((p) => {
      map[p.type] = p.value;
    });

    const formattedY = parseInt(map.year, 10);
    const formattedM = parseInt(map.month, 10);
    const formattedD = parseInt(map.day, 10);
    let formattedH = parseInt(map.hour, 10);
    if (formattedH === 24) formattedH = 0;
    const formattedMin = parseInt(map.minute, 10);
    const formattedSec = parseInt(map.second, 10);

    const formattedUtc = Date.UTC(formattedY, formattedM - 1, formattedD, formattedH, formattedMin, formattedSec);
    const diff = Date.UTC(y, m - 1, d, hh, mm, ss) - formattedUtc;
    if (diff === 0) break;
    targetUtc += diff;
  }
  return new Date(targetUtc).toISOString();
}

/**
 * Converts a UTC ISO string into date (YYYY-MM-DD) and time (HH:mm) in the specified time zone
 */
export function utcInstantToLocalDateTime(isoString, timeZone) {
  if (!isoString) return { dateStr: '', timeStr: '' };
  try {
    const date = new Date(isoString);
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: timeZone || 'UTC',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    }).formatToParts(date);

    const map = {};
    parts.forEach((p) => {
      map[p.type] = p.value;
    });

    let hour = map.hour;
    if (hour === '24') hour = '00';
    return {
      dateStr: `${map.year}-${map.month}-${map.day}`,
      timeStr: `${hour}:${map.minute}`,
    };
  } catch {
    return { dateStr: '', timeStr: '' };
  }
}

/**
 * Formats an ISO timestamp in the given time zone
 */
export function formatTimestampInTz(isoString, timeZone) {
  if (!isoString) return null;
  try {
    return new Intl.DateTimeFormat('en-US', {
      timeZone: timeZone || 'UTC',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      timeZoneName: 'short',
    }).format(new Date(isoString));
  } catch {
    return new Date(isoString).toLocaleString();
  }
}

/**
 * Produces a human-readable summary of a schedule
 */
export function formatScheduleSummary(schedule) {
  if (!schedule) return 'No schedule configured';

  const tz = schedule.timeZone ? `(${schedule.timeZone})` : '';

  if (schedule.scheduleType === 'ONE_TIME') {
    const formatted = formatTimestampInTz(schedule.oneTimeExecutionAt, schedule.timeZone);
    return `One-time on ${formatted || schedule.oneTimeExecutionAt}`;
  }

  if (schedule.scheduleType === 'DAILY') {
    return `Daily at ${schedule.scheduledTime || '--:--'} ${tz}`;
  }

  if (schedule.scheduleType === 'WEEKLY') {
    const days = Array.isArray(schedule.daysOfWeek) && schedule.daysOfWeek.length > 0
      ? schedule.daysOfWeek.map((d) => d.slice(0, 3)).join(', ')
      : schedule.dayOfWeek || 'selected days';
    return `Weekly on ${days} at ${schedule.scheduledTime || '--:--'} ${tz}`;
  }

  return 'Custom schedule';
}

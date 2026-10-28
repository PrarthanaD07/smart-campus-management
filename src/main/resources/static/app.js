const collections = {
  students: {
    title: "Students", singular: "student", icon: "♙",
    fields: [
      { name: "id", label: "Student ID", required: true },
      { name: "name", label: "Full name", required: true, wide: true },
      { name: "email", label: "Email address", type: "email", required: true, wide: true },
      { name: "program", label: "Program", required: true },
      { name: "year", label: "Year of study", type: "number", min: 1, max: 8, required: true }
    ],
    columns: [["id", "Student ID"], ["name", "Name"], ["email", "Email"], ["program", "Program"], ["year", "Year"]]
  },
  faculty: {
    title: "Faculty", singular: "faculty member", icon: "♧",
    fields: [
      { name: "id", label: "Faculty ID", required: true },
      { name: "name", label: "Full name", required: true, wide: true },
      { name: "email", label: "Email address", type: "email", required: true, wide: true },
      { name: "department", label: "Department", required: true },
      { name: "designation", label: "Designation", required: true }
    ],
    columns: [["id", "Faculty ID"], ["name", "Name"], ["email", "Email"], ["department", "Department"], ["designation", "Designation"]]
  },
  courses: {
    title: "Courses", singular: "course", icon: "▤",
    fields: [
      { name: "id", label: "Course ID", required: true },
      { name: "name", label: "Course name", required: true, wide: true },
      { name: "facultyId", label: "Assigned faculty", kind: "faculty", wide: true },
      { name: "credits", label: "Credits", type: "number", min: 1, max: 10, required: true }
    ],
    columns: [["id", "Course ID"], ["name", "Course name"], ["facultyId", "Faculty"], ["credits", "Credits"]]
  },
  resources: {
    title: "Resources", singular: "resource", icon: "⌂",
    fields: [
      { name: "id", label: "Resource ID", required: true },
      { name: "type", label: "Resource type", kind: "type", required: true },
      { name: "name", label: "Resource name", required: true, wide: true },
      { name: "capacity", label: "Capacity", type: "number", min: 1, required: true }
    ],
    columns: [["id", "Resource ID"], ["type", "Type"], ["name", "Resource name"], ["capacity", "Capacity"]]
  },
  activities: {
    title: "Activities", singular: "activity", icon: "✳",
    fields: [
      { name: "id", label: "Activity ID", required: true },
      { name: "name", label: "Activity name", required: true, wide: true },
      { name: "date", label: "Date", type: "date", required: true },
      { name: "location", label: "Location", required: true }
    ],
    columns: [["id", "Activity ID"], ["name", "Activity"], ["date", "Date"], ["location", "Location"]]
  },
  bookings: {
    title: "Bookings", singular: "booking", icon: "▦",
    fields: [
      { name: "id", label: "Booking ID", required: true },
      { name: "resourceId", label: "Campus resource", kind: "resource", required: true },
      { name: "bookedBy", label: "Booked by", kind: "person", required: true, wide: true },
      { name: "startTime", label: "Starts at", type: "datetime-local", required: true },
      { name: "endTime", label: "Ends at", type: "datetime-local", required: true }
    ],
    columns: [["id", "Booking ID"], ["resourceId", "Resource"], ["bookedBy", "Booked by"], ["startTime", "Starts"], ["endTime", "Ends"]]
  },
  enrollments: {
    title: "Enrollments", singular: "enrollment", icon: "⇄",
    fields: [
      { name: "courseId", label: "Course", kind: "course", required: true, wide: true },
      { name: "studentId", label: "Student", kind: "student", required: true, wide: true }
    ],
    columns: [["courseId", "Course"], ["studentId", "Student"]]
  }
};

const state = { view: "overview", records: {}, editing: null };
const $ = (selector) => document.querySelector(selector);
const dialog = $("#record-dialog");

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[character]);
}

function notify(message, isError = false) {
  const notice = $("#notice");
  notice.textContent = message;
  notice.classList.toggle("error", isError);
  notice.hidden = false;
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => { notice.hidden = true; }, 4200);
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { ...(options.body ? { "Content-Type": "application/json" } : {}), ...options.headers }
  });
  if (!response.ok) {
    if (response.status === 401) {
      window.location.assign("/login");
      throw new Error("Your session has expired. Sign in again.");
    }
    let message = "The request could not be completed.";
    try { message = (await response.json()).error || message; } catch { /* The server returned no JSON error. */ }
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}

async function initializeCampus() {
  const response = await fetch("/auth/me", { cache: "no-store" });
  if (response.status === 401) {
    window.location.assign("/login");
    return;
  }
  if (!response.ok) throw new Error("Could not verify your Campusly session.");
  const user = await response.json();
  const initials = user.name.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
  $("#profile-avatar").textContent = initials || "SC";
  $("#top-avatar").textContent = initials || "SC";
  $("#profile-name").textContent = user.name;
  $("#profile-email").textContent = user.email;
  await refreshData();
}

async function refreshData() {
  const names = Object.keys(collections);
  const records = await Promise.all(names.map((name) => api(`/api/${name}`)));
  names.forEach((name, index) => { state.records[name] = records[index]; });
  renderCurrentView();
}

function setView(view) {
  if (!collections[view] && view !== "overview") return;
  state.view = view;
  state.editing = null;
  document.querySelectorAll("[data-view]").forEach((button) => {
    button.classList.toggle("active", button.classList.contains("nav-item") && button.dataset.view === view);
  });
  $("#dashboard-view").hidden = view !== "overview";
  $("#records-view").hidden = view === "overview";
  const title = view === "overview" ? "Overview" : collections[view].title;
  $("#breadcrumb-current").textContent = title;
  $("#page-title").innerHTML = view === "overview"
    ? 'Good day, Admin <span class="wave">✳</span>'
    : escapeHtml(title);
  $("#page-eyebrow").textContent = view === "overview" ? "CAMPUS AT A GLANCE" : "CAMPUS DIRECTORY";
  $("#page-subtitle").textContent = view === "overview"
    ? "Here’s what’s happening across your campus today."
    : `View, search and manage your ${title.toLowerCase()}.`;
  $("#primary-action-label").textContent = `Add ${view === "overview" ? "student" : collections[view].singular}`;
  $("#search-input").value = "";
  renderCurrentView();
}

function renderCurrentView() {
  if (state.view === "overview") {
    renderOverview();
    return;
  }
  renderTable(state.view);
}

function renderOverview() {
  const counts = {
    students: (state.records.students || []).length,
    faculty: (state.records.faculty || []).length,
    courses: (state.records.courses || []).length,
    resources: (state.records.resources || []).length,
    activities: (state.records.activities || []).length,
    bookings: (state.records.bookings || []).length
  };
  const stats = [
    ["Students", counts.students, "♙", "#d16455", "#fff0eb"],
    ["Faculty", counts.faculty, "♧", "#8558c7", "#f3edff"],
    ["Courses", counts.courses, "▤", "#3679d0", "#eaf3ff"],
    ["Resources", counts.resources, "⌂", "#29865f", "#e8f7ef"],
    ["Activities", counts.activities, "✳", "#c58a27", "#fff5dc"],
    ["Bookings", counts.bookings, "▦", "#d15f8b", "#fff0f5"]
  ];
  $("#stats-grid").innerHTML = stats.map(([label, number, icon, color, soft]) => `
    <button class="stat-card" data-view="${label.toLowerCase()}" style="--stat-color:${color};--stat-soft:${soft}" aria-label="View ${label.toLowerCase()}">
      <div class="stat-top"><span>${label}</span><span class="stat-icon">${icon}</span></div>
      <strong class="stat-number">${number}</strong>
    </button>`).join("");
  Object.entries(counts).forEach(([name, count]) => {
    const target = $(`#overview-${name}`);
    if (target) target.textContent = count;
  });
  const enrollments = (state.records.enrollments || []).slice(0, 4);
  $("#recent-enrollments").hidden = enrollments.length > 0;
  let list = $(".enrollment-list");
  if (!list) {
    list = document.createElement("div");
    list.className = "enrollment-list";
    $("#recent-enrollments").after(list);
  }
  list.hidden = enrollments.length === 0;
  list.innerHTML = enrollments.map((item) => {
    const course = (state.records.courses || []).find((row) => row.id === item.courseId);
    const student = (state.records.students || []).find((row) => row.id === item.studentId);
    return `<div class="enrollment-row"><strong>${escapeHtml(course?.name || item.courseId)}</strong><span>${escapeHtml(student?.name || item.studentId)}</span></div>`;
  }).join("");
  renderActivityPreview();
  renderResourceBreakdown();
  renderPulseChart(counts);
  renderUpcoming();
}

function renderPulseChart(counts) {
  const series = [
    ["Students", counts.students, "#ed8b72"],
    ["Faculty", counts.faculty, "#9a7ce0"],
    ["Courses", counts.courses, "#6a9ce2"],
    ["Resources", counts.resources, "#52b995"],
    ["Activities", counts.activities, "#e4ae53"],
    ["Bookings", counts.bookings, "#de77a0"]
  ];
  const max = Math.max(1, ...series.map(([, value]) => value));
  $("#pulse-bars").innerHTML = series.map(([label, value, color]) => `
    <button class="pulse-column" data-view="${label.toLowerCase()}" aria-label="View ${label}: ${value}">
      <span class="pulse-value">${value}</span>
      <span class="pulse-bar" style="--bar-height:${Math.max(value ? 9 : 3, value / max * 100)}%;--bar-color:${color}"></span>
      <span class="pulse-label">${label}</span>
    </button>`).join("");
  $("#pulse-legend").innerHTML = series.map(([label, , color]) =>
    `<span><i style="--legend-color:${color}"></i>${label}</span>`).join("");
}

function renderUpcoming() {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const items = [
    ...(state.records.activities || []).map((activity) => ({
      kind: "activity", title: activity.name, location: activity.location,
      when: `${activity.date}T00:00:00`, dateLabel: activity.date, view: "activities"
    })),
    ...(state.records.bookings || []).map((booking) => ({
      kind: "booking", title: (state.records.resources || []).find((resource) => resource.id === booking.resourceId)?.name || booking.resourceId,
      location: `Booked by ${(state.records.students || []).concat(state.records.faculty || []).find((person) => person.id === booking.bookedBy)?.name || booking.bookedBy}`,
      when: booking.startTime, dateLabel: booking.startTime.replace("T", " "), view: "bookings"
    }))
  ].filter((item) => new Date(item.when) >= today)
    .sort((left, right) => left.when.localeCompare(right.when))
    .slice(0, 5);
  const container = $("#upcoming-list");
  if (!items.length) {
    container.innerHTML = `<div class="schedule-empty"><span class="schedule-empty-icon">☀</span><strong>Nothing on the calendar just yet</strong><small>Add an activity or reserve a space to see what’s coming up.</small><div><button class="mini-cta" data-add="activities">＋ Add activity</button><button class="mini-cta booking-mini" data-add="bookings">＋ New booking</button></div></div>`;
    return;
  }
  container.innerHTML = items.map((item, index) => {
    const date = new Date(item.when);
    const month = new Intl.DateTimeFormat(undefined, { month: "short" }).format(date);
    const day = new Intl.DateTimeFormat(undefined, { day: "2-digit" }).format(date);
    return `<article class="upcoming-item upcoming-tone-${index % 4 + 1}">
      <div class="upcoming-date"><span>${escapeHtml(month)}</span><strong>${escapeHtml(day)}</strong></div>
      <div class="upcoming-copy"><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(item.location)}</small></div>
      <span class="upcoming-kind">${item.kind === "activity" ? "EVENT" : "BOOKING"}</span>
    </article>`;
  }).join("");
}

function renderActivityPreview() {
  const activities = [...(state.records.activities || [])]
    .sort((left, right) => left.date.localeCompare(right.date))
    .slice(0, 3);
  const container = $("#activity-preview");
  if (!activities.length) {
    container.innerHTML = `<div class="insight-empty"><span class="empty-illustration">✦</span><strong>Room for a little magic</strong><small>Plan an event and it’ll show up here.</small><button class="mini-cta" data-add="activities">＋ Add an activity</button></div>`;
    return;
  }
  container.innerHTML = activities.map((activity, index) => {
    const date = new Date(`${activity.date}T12:00:00`);
    const month = new Intl.DateTimeFormat(undefined, { month: "short" }).format(date);
    const day = new Intl.DateTimeFormat(undefined, { day: "2-digit" }).format(date);
    return `<article class="activity-card activity-tone-${index + 1}">
      <div class="activity-date"><span>${escapeHtml(month)}</span><strong>${escapeHtml(day)}</strong></div>
      <div class="activity-info"><strong>${escapeHtml(activity.name)}</strong><small>⌖ ${escapeHtml(activity.location)}</small></div>
      <span class="activity-arrow">↗</span>
    </article>`;
  }).join("");
}

function renderResourceBreakdown() {
  const resources = state.records.resources || [];
  const types = [
    ["Classroom", "Classrooms", "#6070d2"],
    ["Laboratory", "Laboratories", "#43a889"],
    ["Equipment", "Equipment", "#e4a247"]
  ];
  const max = Math.max(1, ...types.map(([type]) => resources.filter((resource) => resource.type === type).length));
  $("#resource-breakdown").innerHTML = types.map(([type, label, color]) => {
    const count = resources.filter((resource) => resource.type === type).length;
    return `<div class="resource-type-row">
      <div class="resource-type-label"><span class="resource-type-dot" style="--type-color:${color}"></span><span>${label}</span><b>${count}</b></div>
      <div class="resource-track"><span style="--type-color:${color};--bar-width:${Math.round(count / max * 100)}%"></span></div>
    </div>`;
  }).join("");
  $("#resource-total").textContent = resources.length;
}

function displayValue(key, value, row) {
  if (key === "facultyId") {
    return escapeHtml((state.records.faculty || []).find((person) => person.id === value)?.name || value || "Unassigned");
  }
  if (key === "resourceId") {
    return escapeHtml((state.records.resources || []).find((resource) => resource.id === value)?.name || value);
  }
  if (key === "bookedBy") {
    const person = [...(state.records.students || []), ...(state.records.faculty || [])]
      .find((candidate) => candidate.id === value);
    return escapeHtml(person?.name || value);
  }
  if (key === "courseId") {
    return escapeHtml((state.records.courses || []).find((course) => course.id === value)?.name || value);
  }
  if (key === "studentId") {
    return escapeHtml((state.records.students || []).find((student) => student.id === value)?.name || value);
  }
  if (key === "year") return `Year ${escapeHtml(value)}`;
  if (key === "type") return `<span class="tag">${escapeHtml(value)}</span>`;
  if ((key === "startTime" || key === "endTime") && value) {
    return escapeHtml(String(value).replace("T", " "));
  }
  if (key === "name") return `<span class="cell-primary">${escapeHtml(value)}</span>`;
  if (key === "id") return `<span class="cell-id">${escapeHtml(value)}</span>`;
  return escapeHtml(value);
}

function renderTable(name) {
  const config = collections[name];
  const records = state.records[name] || [];
  const query = $("#search-input").value.trim().toLowerCase();
  const filtered = records.filter((record) => JSON.stringify(record).toLowerCase().includes(query));
  $("#record-count").textContent = `${filtered.length} ${filtered.length === 1 ? "record" : "records"}`;
  $("#table-head").innerHTML = `<tr>${config.columns.map(([, label]) => `<th>${escapeHtml(label)}</th>`).join("")}<th><span class="sr-only">Actions</span></th></tr>`;
  $("#table-body").innerHTML = filtered.map((row) => `
    <tr>${config.columns.map(([key]) => `<td>${displayValue(key, row[key], row)}</td>`).join("")}
      <td><div class="row-actions">
        <button class="row-action" data-edit="${escapeHtml(encodeURIComponent(row.id))}" aria-label="Edit or update ${escapeHtml(config.singular)}" title="Edit / update"><span aria-hidden="true">✎</span><span>Edit</span></button>
        <button class="row-action delete" data-delete="${escapeHtml(encodeURIComponent(row.id))}" aria-label="Delete ${escapeHtml(config.singular)}" title="Delete"><span aria-hidden="true">×</span><span>Delete</span></button>
      </div></td>
    </tr>`).join("");
  $("#table-empty").hidden = filtered.length > 0;
  $(".table-wrap").hidden = filtered.length === 0;
}

function optionsFor(kind) {
  const records = {
    faculty: state.records.faculty || [],
    course: state.records.courses || [],
    student: state.records.students || [],
    resource: state.records.resources || []
  }[kind] || [];
  if (kind === "person") {
    return [
      ...(state.records.students || []).map((item) => ({ id: item.id, name: `${item.name} · Student` })),
      ...(state.records.faculty || []).map((item) => ({ id: item.id, name: `${item.name} · Faculty` }))
    ];
  }
  return records.map((item) => ({
    id: item.id,
    name: item.name ? `${item.name} (${item.id})` : item.id
  }));
}

function fieldMarkup(field, value, isEditing) {
  let input;
  if (field.kind) {
    let choices;
    if (field.kind === "type") choices = ["Classroom", "Laboratory", "Equipment"].map((type) => ({ id: type, name: type }));
    else choices = optionsFor(field.kind);
    const emptyLabel = field.kind === "faculty" ? "Unassigned" : `Select ${field.label.toLowerCase()}`;
    input = `<select id="field-${field.name}" name="${field.name}" ${field.required ? "required" : ""}>
      ${field.kind === "faculty" ? `<option value="">${emptyLabel}</option>` : '<option value="">Choose an option</option>'}
      ${choices.map((item) => `<option value="${escapeHtml(item.id)}" ${String(value ?? "") === String(item.id) ? "selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}
    </select>`;
  } else {
    if (field.type === "datetime-local" && value) value = String(value).slice(0, 16);
    const attr = [
      `type="${escapeHtml(field.type || "text")}"`,
      field.required ? "required" : "",
      field.min !== undefined ? `min="${field.min}"` : "",
      field.max !== undefined ? `max="${field.max}"` : "",
      field.type === "number" ? 'step="1"' : "",
      field.name === "id" && isEditing ? "disabled" : "",
      `value="${escapeHtml(value ?? "")}"`
    ].filter(Boolean).join(" ");
    input = `<input id="field-${field.name}" name="${field.name}" ${attr} autocomplete="off">`;
  }
  return `<div class="form-field ${field.wide ? "wide" : ""}"><label for="field-${field.name}">${escapeHtml(field.label)}</label>${input}</div>`;
}

function openForm(name, record = null) {
  state.editing = record;
  const config = collections[name];
  $("#dialog-eyebrow").textContent = record ? "EDIT RECORD" : "NEW RECORD";
  $("#dialog-title").textContent = `${record ? "Edit" : "Add"} ${config.singular}`;
  $("#save-button").textContent = record ? "Save changes" : "Save record";
  $("#form-fields").innerHTML = config.fields.map((field) => fieldMarkup(field, record?.[field.name], Boolean(record))).join("");
  $("#record-form").dataset.collection = name;
  dialog.showModal();
}

async function deleteRecord(name, id) {
  const config = collections[name];
  if (!confirm(`Delete this ${config.singular}? This cannot be undone.`)) return;
  try {
    await api(`/api/${name}/${encodeURIComponent(id)}`, { method: "DELETE" });
    await refreshData();
    notify(`${config.singular[0].toUpperCase()}${config.singular.slice(1)} deleted.`);
  } catch (error) {
    notify(error.message, true);
  }
}

function navigateFromClick(event) {
  const target = event.target.closest("[data-view]");
  if (target) setView(target.dataset.view);
}

document.addEventListener("click", (event) => {
  const searchResult = event.target.closest("[data-search-result]");
  if (searchResult) {
    const { searchResult: view, searchValue } = searchResult.dataset;
    setView(view);
    $("#search-input").value = searchValue;
    renderTable(view);
    $("#global-search").value = "";
    $("#global-search-results").hidden = true;
    return;
  }
  const add = event.target.closest("[data-add]");
  if (add) {
    openForm(add.dataset.add);
    return;
  }
  const edit = event.target.closest("[data-edit]");
  const remove = event.target.closest("[data-delete]");
  if (edit) {
    const id = decodeURIComponent(edit.dataset.edit);
    const row = (state.records[state.view] || []).find((item) => item.id === id);
    if (row) openForm(state.view, row);
    return;
  }
  if (remove) {
    deleteRecord(state.view, decodeURIComponent(remove.dataset.delete));
    return;
  }
  navigateFromClick(event);
});

document.querySelectorAll(".nav-item").forEach((button) => {
  button.addEventListener("click", () => setView(button.dataset.view));
});
$(".brand").addEventListener("click", (event) => {
  event.preventDefault();
  setView("overview");
});
$("#primary-action").addEventListener("click", () => openForm(state.view === "overview" ? "students" : state.view));
$("#search-input").addEventListener("input", () => renderTable(state.view));
$("#global-search").addEventListener("input", (event) => {
  const query = event.target.value.trim().toLowerCase();
  const results = $("#global-search-results");
  if (query.length < 2) {
    results.hidden = true;
    results.innerHTML = "";
    return;
  }
  const matches = Object.entries(collections).flatMap(([view, config]) =>
    (state.records[view] || [])
      .filter((record) => JSON.stringify(record).toLowerCase().includes(query))
      .slice(0, 4)
      .map((record) => ({
        view,
        label: record.name || (record.resourceId
          ? (state.records.resources || []).find((resource) => resource.id === record.resourceId)?.name || record.resourceId
          : record.courseId ? `${record.courseId} · ${record.studentId}` : record.id),
        detail: `${config.title} · ${record.id || record.courseId}`,
        searchValue: record.id || `${record.courseId} ${record.studentId}`
      }))
  ).slice(0, 8);
  results.innerHTML = matches.length
    ? matches.map((match) => `<button class="global-result" data-search-result="${match.view}" data-search-value="${escapeHtml(match.searchValue)}"><span class="global-result-icon">${escapeHtml(collections[match.view].icon)}</span><span><strong>${escapeHtml(match.label)}</strong><small>${escapeHtml(match.detail)}</small></span><i>↗</i></button>`).join("")
    : `<div class="global-no-results">No matches found for “${escapeHtml(event.target.value.trim())}”.</div>`;
  results.hidden = false;
});
$("#global-search").addEventListener("keydown", (event) => {
  if (event.key === "Escape") $("#global-search-results").hidden = true;
  if (event.key === "Enter") $(".global-result")?.click();
});
document.addEventListener("click", (event) => {
  if (!event.target.closest(".global-search-wrap")) $("#global-search-results").hidden = true;
});
$("#theme-toggle").addEventListener("click", () => {
  const dark = document.body.dataset.theme !== "dark";
  document.body.dataset.theme = dark ? "dark" : "light";
  localStorage.setItem("campusly-theme", document.body.dataset.theme);
  $("#theme-toggle").textContent = dark ? "☀" : "☾";
  $("#theme-toggle").title = dark ? "Switch to light theme" : "Switch to dark theme";
  $("#theme-toggle").setAttribute("aria-label", $("#theme-toggle").title);
});
$("#close-dialog").addEventListener("click", () => dialog.close());
$("#cancel-dialog").addEventListener("click", () => dialog.close());
$("#record-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const collection = form.dataset.collection;
  const config = collections[collection];
  const values = Object.fromEntries(new FormData(form).entries());
  if (state.editing && config.fields.some((field) => field.name === "id")) {
    values.id = state.editing.id;
  }
  config.fields.forEach((field) => {
    if (field.type === "number") values[field.name] = Number(values[field.name]);
  });
  if (collection === "enrollments") {
    values.courseId = $("#field-courseId").value;
    values.studentId = $("#field-studentId").value;
  }
  try {
    const url = state.editing
      ? `/api/${collection}/${encodeURIComponent(state.editing.id)}`
      : `/api/${collection}`;
    await api(url, { method: state.editing ? "PUT" : "POST", body: JSON.stringify(values) });
    dialog.close();
    await refreshData();
    notify(`${config.singular[0].toUpperCase()}${config.singular.slice(1)} ${state.editing ? "updated" : "saved"}.`);
    state.editing = null;
  } catch (error) {
    notify(error.message, true);
  }
});
$("#export-button").addEventListener("click", async () => {
  try {
    const response = await fetch("/api/export");
    if (!response.ok) throw new Error("Could not export the student list.");
    const file = await response.blob();
    const link = document.createElement("a");
    link.href = URL.createObjectURL(file);
    link.download = "students.csv";
    link.click();
    URL.revokeObjectURL(link.href);
    notify("Student CSV downloaded.");
  } catch (error) {
    notify(error.message, true);
  }
});
$("#logout-button").addEventListener("click", async () => {
  try {
    await fetch("/auth/logout", { method: "POST" });
    window.location.assign("/login");
  } catch {
    notify("Could not sign out. Please try again.", true);
  }
});

$("#today-label").textContent = new Intl.DateTimeFormat(undefined, {
  weekday: "short", month: "short", day: "numeric", year: "numeric"
}).format(new Date());

if (localStorage.getItem("campusly-theme") === "dark") {
  document.body.dataset.theme = "dark";
  $("#theme-toggle").textContent = "☀";
  $("#theme-toggle").title = "Switch to light theme";
  $("#theme-toggle").setAttribute("aria-label", "Switch to light theme");
}
document.addEventListener("keydown", (event) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    $("#global-search").focus();
  }
});

initializeCampus().catch((error) => notify(error.message, true));

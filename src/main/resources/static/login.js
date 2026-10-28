const errorMessages = {
  cancelled: "Sign-in was cancelled. You can try again whenever you’re ready.",
  "sign-in-failed": "We couldn’t verify that sign-in. Please try again.",
  "google-unavailable": "Google sign-in is temporarily unavailable. Please try again.",
  "not-configured": "Google sign-in hasn’t been configured for this app yet."
};

const errorCode = new URLSearchParams(window.location.search).get("error");
if (errorCode && errorMessages[errorCode]) {
  const error = document.querySelector("#login-error");
  error.textContent = errorMessages[errorCode];
  error.hidden = false;
}

fetch("/auth/config")
  .then((response) => {
    if (!response.ok) throw new Error("Could not check the Google sign-in configuration.");
    return response.json();
  })
  .then((configuration) => {
    if (!configuration.configured) {
      document.querySelector("#setup-note").hidden = false;
      document.querySelector("#google-button").classList.add("disabled");
      document.querySelector("#google-button").setAttribute("aria-disabled", "true");
      document.querySelector("#google-button").addEventListener("click", (event) => event.preventDefault());
    }
  })
  .catch(() => {
    const error = document.querySelector("#login-error");
    error.textContent = "Could not contact the sign-in service. Check that the Campusly server is running.";
    error.hidden = false;
  });

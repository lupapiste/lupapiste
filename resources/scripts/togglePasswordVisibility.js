"use strict";

function togglePasswordVisibility(event, passwordInputId) {
  var passwordInput = document.getElementById(passwordInputId);
  if (passwordInput.type === "password") {
    passwordInput.type = "text";
    passwordInput.autocomplete = "off";
  } else {
    passwordInput.type = "password";
    passwordInput.autocomplete = "current-password";
  }
}

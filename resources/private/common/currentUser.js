var currentUser = (function() {
  "use strict";

  var user = {};

  return {
    set: function(u) { user = u; },
    get: function() { return user; },
    isAuthority: function() { return user.role === "authority"; },
    isApplicant: function() { return user.role === "applicant"; }
  };

})();

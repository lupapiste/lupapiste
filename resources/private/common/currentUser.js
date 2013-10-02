var currentUser = (function() {
  "use strict";

  var user = ko.mapping.fromJS({id:             "",
                                email:          "",
                                role:           "",
                                oir:            "",
                                organizations:  [],
                                firstName:      "",
                                lastName:       "",
                                phone:          "",
                                username:       ""});

  return {
    set: function(u) { ko.mapping.fromJS(u, user); },
    get: function() { return user; },
    isAuthority: function() { return user.role() === "authority"; },
    isApplicant: function() { return user.role() === "applicant"; },
    id: function() { return user.id(); }
  };

})();

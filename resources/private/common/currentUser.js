var currentUser = (function() {
  "use strict";

  var user = ko.mapping.fromJS({
    id:             "",
    email:          "",
    role:           "",
    oir:            "",
    organizations:  [],
    firstName:      "",
    lastName:       "",
    phone:          "",
    username:       "",
    company: {
      id:   null,
      role: null
    }});

  return {
    set: function(u) { ko.mapping.fromJS(u, user); },
    get: function() { return user; },
    isAuthority: function() { return user.role() === "authority"; },
    isApplicant: function() { return user.role() === "applicant"; },
    isCompanyUser: ko.pureComputed( function() { return !_.isEmpty(ko.unwrap(user.company.id)); }),
    id: function() { return user.id(); },
    displayName: ko.pureComputed( function() {
      var username = ko.unwrap(user.username) || "";
      if (ko.unwrap(user.firstName) || ko.unwrap(user.lastName)) {
        username = ko.unwrap(user.firstName) + " " + ko.unwrap(user.lastName);
      }
      return username;
    })
  };

})();

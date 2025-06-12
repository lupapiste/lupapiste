LUPAPISTE.AssignmentRecipientFilterService = function() {
  "use strict";
  var self = this;

  // dummy elements for autocomplete
  self.myown = {id: "my-own", fullName: loc("applications.search.recipient.my-own"), behaviour: "singleSelection"};
  self.all = {id: "all", fullName: loc("all"), behaviour: "clearSelected"};
  self.noone = {id: null, fullName: loc("applications.search.recipient.no-one"), behaviour: "singleSelection"};

  self.selected = ko.observableArray([self.myown]);

  var usersInSameOrganizations = lupapisteApp.services.organizationsUsersService.users;

  self.data = ko.pureComputed(function() {
    return usersInSameOrganizations();
  });

};

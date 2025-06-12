LUPAPISTE.HandlerFilterService = function(applicationFiltersService) {
  "use strict";
  var self = this;

  self.selected = ko.observableArray([]);

  // dummy elements for autocomplete
  self.noAuthority = {id: "no-authority", fullName: loc("applications.search.handler.no-authority"), behaviour: "singleSelection"};
  self.all = {id: "all", fullName: loc("all"), behaviour: "clearSelected"};

  var savedFilter = ko.pureComputed(function() {
    return util.getIn(applicationFiltersService.selected(), ["filter", "handlers"]);
  });

  var usersInSameOrganizations = lupapisteApp.services.organizationsUsersService.users;

  ko.computed(function() {
    if (savedFilter() && _.includes(savedFilter(), "no-authority")) {
      self.selected([self.noAuthority]);
    } else {
      self.selected(_.filter(usersInSameOrganizations(),
        function (user) {
          if (savedFilter()) {
            return _.includes(savedFilter(), user.id);
          }
        }));
    }
  });

  self.data = ko.pureComputed(function() {
    return usersInSameOrganizations();
  });
};

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

  var usersInSameOrganizations = ko.observable();

  ko.computed(function() {
    if (savedFilter() && _.contains(savedFilter(), "no-authority")) {
      self.selected([self.noAuthority]);
    } else {
      self.selected(_.filter(usersInSameOrganizations(),
        function (user) {
          if (savedFilter()) {
            return _.contains(savedFilter(), user.id);
          }
        }));
    }
  });

  self.data = ko.pureComputed(function() {
    return usersInSameOrganizations();
  });

  function mapUser(user) {
    user.fullName = _.filter([user.lastName, user.firstName]).join("\u00a0");
    return user;
  }

  hub.subscribe("global-auth-model-loaded", function(){
    if (lupapisteApp.models.globalAuthModel.ok("users-in-same-organizations")) {
      ajax
        .query("users-in-same-organizations")
        .success(function(res) {
          usersInSameOrganizations(_(res.users).map(mapUser).sortBy("fullName").value());
        })
        .call();
    }
  }, true);
};

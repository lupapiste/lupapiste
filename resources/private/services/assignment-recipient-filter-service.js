LUPAPISTE.AssignmentRecipientFilterService = function() {
  "use strict";
  var self = this;

  // dummy elements for autocomplete
  self.myown = {id: "my-own", fullName: loc("applications.search.recipient.my-own"), behaviour: "singleSelection"};
  self.all = {id: "all", fullName: loc("all"), behaviour: "clearSelected"};

  self.selected = ko.observableArray([self.myown]);

  /*var savedFilter = ko.pureComputed(function() {
    return util.getIn(applicationFiltersService.selected(), ["filter", "recipient"]);
  });*/

  var usersInSameOrganizations = ko.observable();

  /*ko.computed(function() {
    if (savedFilter() && _.includes(savedFilter(), "my-own")) {
      self.selected(["my-own"]);
    } else {
      self.selected(_.filter(usersInSameOrganizations(),
        function (user) {
          if (savedFilter()) {
            return _.includes(savedFilter(), user.id);
          }
        }));
    }
  }); */

  self.data = ko.pureComputed(function() {
    return usersInSameOrganizations();
  });

  function mapUser(user) {
    user.fullName = _.filter([user.lastName, user.firstName]).join("\u00a0");
    return user;
  }

  function load() {
    if (lupapisteApp.models.globalAuthModel.ok("users-in-same-organizations")) {
      ajax
        .query("users-in-same-organizations")
        .success(function(res) {
          usersInSameOrganizations(_(res.users).map(mapUser).sortBy("fullName").value());
        })
        .call();
      return true;
    }
    return false;
  }

  if (!load()) {
    hub.subscribe("global-auth-model-loaded", load, true);
  }

};

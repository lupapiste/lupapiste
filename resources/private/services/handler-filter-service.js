LUPAPISTE.HandlerFilterService = function() {
  "use strict";
  var self = this;

  self.selected = ko.observableArray([]);

  var defaultFilter = ko.pureComputed(function() {
    return util.getIn(lupapisteApp.models.currentUser, ["applicationFilters", 0, "filter", "handlers"]);
  });

  var usersInSameOrganizations = ko.observable();

  ko.computed(function() {
    self.selected(_.filter(usersInSameOrganizations(), function (user) {return _.contains(defaultFilter(), user.id)}));
  });

  self.data = ko.pureComputed(function() {
    return usersInSameOrganizations();
  });

  // add default filter items when data or filter updates
  ko.computed(function() {
    ko.utils.arrayPushAll(self.selected,
      _.filter(self.data(), function(user) {
        return _.contains(defaultFilter(), user.id);
      }));
  });

  function mapUser(user) {
    user.fullName = _.filter([user.lastName, user.firstName]).join("\u00a0");
    return user;
  }

  ajax
    .query("users-in-same-organizations")
    .error(_.noop)
    .success(function(res) {
      usersInSameOrganizations(_(res.users).map(mapUser).sortBy("fullName").value());
    })
    .call();
};
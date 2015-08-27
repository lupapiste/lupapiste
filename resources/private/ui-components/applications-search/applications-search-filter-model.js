

LUPAPISTE.HandlersDataProvider = function() {
  "use strict";
  var self = this;

  self.query = ko.observable();

  var data = ko.observable();

  function mapUser(user) {
    var fullName = "";
    if (user) {
      if (user.lastName) { fullName += user.lastName; }
      if (user.firstName && user.lastName) { fullName += "\u00a0"; }
      if (user.firstName) { fullName +=  user.firstName; }
    }
    user.fullName = fullName;
    return user;
  }

  ajax
    .query("users-in-same-organizations")
    .error(_.noop)
    .success(function(res) {
      data(_(res.users).map(mapUser).sortBy("fullName").value());
    })
    .call();

  self.data = ko.pureComputed(function() {
    var q = self.query() || "";
    return _.filter(data(), function(item) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(item.fullName.toUpperCase(), word.toUpperCase()) && result;
      }, true);
    });
  });
};

LUPAPISTE.ApplicationsSearchFilterModel = function(params) {
  "use strict";
  var self = this;

  self.dataProvider = params.dataProvider;

  self.handlersDataProvider = null;
  self.areasDataProvider = null;

  self.showAdvancedFilters = ko.observable(false);
  self.advancedFiltersText = ko.computed(function() {
    return self.showAdvancedFilters() ? "applications.filter.advancedFilter.hide" : "applications.filter.advancedFilter.show";
  });

  self.saveAdvancedFilters = function() {
    var filter = {
      tags:          _.map(ko.unwrap(self.dataProvider.tags), "id"),
      operations:    _.map(ko.unwrap(self.dataProvider.operations), "id"),
      organizations: _.map(ko.unwrap(self.dataProvider.organizations), "id"),
      areas:         _.map(ko.unwrap(self.dataProvider.areas), "id")
    };

    ajax
    .command("update-default-application-filter", {filter: filter})
    .error(_.noop)
    .success(function() {
      // TODO show indicator for success
    })
    .call();
  };

  if ( lupapisteApp.models.currentUser.isAuthority() ) {
    self.handlersDataProvider = new LUPAPISTE.HandlersDataProvider();
  }
};

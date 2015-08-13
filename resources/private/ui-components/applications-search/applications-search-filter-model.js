LUPAPISTE.OrganizationTagsDataProvider = function(organization, filtered) {
  "use strict";
  var self = this;

  self.query = ko.observable();

  self.filtered = filtered || ko.observableArray([]);

  var data = ko.observable();

  if (organization && util.getIn(lupapisteApp.models.currentUser, ["orgAuthz", organization])) {
    ajax
      .query("get-organization-tags", {organizationId: organization})
      .error(_.noop)
      .success(function(res) {
        data(res.tags);
      })
      .call();
  }

  self.data = ko.pureComputed(function() {
    var filteredData = _.filter(data(), function(tag) {
      return !_.some(self.filtered(), tag);
    });
    var q = self.query() || "";
    filteredData = _.filter(filteredData, function(tag) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(tag.label.toUpperCase(), word.toUpperCase()) && result;
      }, true);
    });
    return filteredData;
  });
};

LUPAPISTE.OperationsDataProvider = function() {
  "use strict";
  var self = this;

  var operations = ko.observable();

  ajax
    .query("get-application-operations")
    .error(_.noop)
    .success(function(res) {
      console.log(res);
      operations(res.operations);
    })
    .call();

  self.data = ko.pureComputed(function() {
    var operationItems = _.map(operations(), function(operation) {
      return {label: loc("operations." + operation)};
    });
    return operationItems;
  });

  self.query = ko.observable();
};

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

  self.organizationTagsDataProvider = null;
  self.operationsDataProvider = null;
  self.handlersDataProvider = null;

  self.showAdvancedFilters = ko.observable(false);
  self.advancedFiltersText = ko.computed(function() {
    return self.showAdvancedFilters() ? "applications.filter.advancedFilter.hide" : "applications.filter.advancedFilter.show";
  });

  if ( lupapisteApp.models.currentUser.isAuthority() ) {
    self.handlersDataProvider = new LUPAPISTE.HandlersDataProvider();
    self.operationsDataProvider = new LUPAPISTE.OperationsDataProvider();
    // TODO just search single organization tags for now, later do some grouping stuff in autocomplete component
    self.organizationTagsDataProvider = new LUPAPISTE.OrganizationTagsDataProvider(_.last(_.keys(lupapisteApp.models.currentUser.orgAuthz())), self.dataProvider.applicationTags);
  }
};

LUPAPISTE.OrganizationTagsDataProvider = function(filtered) {
  "use strict";
  var self = this;

  self.query = ko.observable();

  self.filtered = filtered || ko.observableArray([]);

  var tagsData = ko.observable();

  ajax
    .query("get-organization-tags")
    .error(_.noop)
    .success(function(res) {
      tagsData(res.tags);
    })
    .call();

  self.groupedData = ko.pureComputed(function() {
    // first filter out those tags who are not selected
    var filteredData = _.map(tagsData(), function(orgData) {
      return {organization: orgData.name[loc.currentLanguage], tags: _.filter(orgData.tags, function(tag) {
        return !_.some(self.filtered(), tag);
      })};
    });
    var q = self.query() || "";
    // then filter tags that don't match query
    filteredData = _.map(filteredData, function(orgData) {
      return _.assign(orgData, {tags: _.filter(orgData.tags, function(tag) {
        return _.reduce(q.split(" "), function(result, word) {
          return _.contains(tag.label.toUpperCase(), word.toUpperCase()) && result;
        }, true);
      })});
    });
    // last filter out organization objects whose tags are empty
    filteredData = _.filter(filteredData, function(orgData) {
      return !_.isEmpty(orgData.tags);
    });
    return filteredData;
  });

  function firstGroupData() { // returns tags of first organization
    return _.first(self.groupedData()).tags || []
  }

  self.hasGroups = ko.pureComputed(function() {
    return _.keys(tagsData()).length > 1
  });

  self.data = ko.pureComputed(function() {
    return self.hasGroups() ? self.groupedData() : firstGroupData();
  });
};

LUPAPISTE.OperationsDataProvider = function() {
  "use strict";
  var self = this;

  var operationsByPermitType = ko.observable();

  ajax
    .query("get-application-operations")
    .error(_.noop)
    .success(function(res) {
      operationsByPermitType(res.operationsByPermitType);
    })
    .call();

  function localizeOperations(operations) {
    return _.map(operations, function(op) {
      return loc("operations." + op)
    });
  }

  function wrapInObject(operations) {
    return _.map(operations, function(op) {
      return {label: op}
    });
  }

  self.data = ko.pureComputed(function() {
    var result = _.map(operationsByPermitType(), function(operations, permitType) {
      return {
        permitType: loc(permitType),
        operations: _.flow(localizeOperations, wrapInObject)(operations)
      }
    })
    return result;
  });

  self.hasGroups = ko.pureComputed(function() {
    return _.keys(operationsByPermitType()).length > 1
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
    self.organizationTagsDataProvider = new LUPAPISTE.OrganizationTagsDataProvider(self.dataProvider.applicationTags);
  }
};

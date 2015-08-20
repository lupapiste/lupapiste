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

  self.groupData = ko.pureComputed(function() { // when user belongs to more than one organization
    return _.keys(tagsData()).length > 1 ? {header: "organization", dataProperty: "tags"} : null;
  });

  self.groupedFilter = ko.pureComputed(function() {
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

  function getFirstTags() { // returns tags of first organization
    return _.isEmpty(self.groupedFilter()) ? [] : _.first(self.groupedFilter()).tags;
  }

  self.data = ko.pureComputed(function() {
    return _.keys(tagsData()).length > 1 ? self.groupedFilter() : getFirstTags();
  });
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

LUPAPISTE.OrganizationsFilterDataProvider = function(savedOrgFilters) {
  "use strict";

  var self = this;

  self.query = ko.observable();
  self.savedOrgFilters = savedOrgFilters || ko.observableArray([]);

  var data = ko.observable();
  var usersOwnOrganizations = _.keys(lupapisteApp.models.currentUser.orgAuthz());

  ajax
  .query("get-organization-names")
  .error(_.noop)
  .success(function(res) {
    var ownOrgsWithNames = _.map(usersOwnOrganizations, function(org) {
      return {id: org, name: res.names[org][loc.currentLanguage]};
    });
    data(ownOrgsWithNames);
  })
  .call();

  self.data = ko.pureComputed(function() {
    var q = self.query() || "";
    return _.filter(data(), function(item) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(item.name.toUpperCase(), word.toUpperCase())
               && !_.some(self.savedOrgFilters(), function(orgFilter) { return item.id === orgFilter.id; })
               && result;
      }, true);
    });
  });

};

LUPAPISTE.ApplicationsSearchFilterModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;

  self.organizationTagsDataProvider = null;
  self.handlersDataProvider = null;

  self.showAdvancedFilters = ko.observable(false);
  self.advancedFiltersText = ko.computed(function() {
    return self.showAdvancedFilters() ? "applications.filter.advancedFilter.hide" : "applications.filter.advancedFilter.show";
  });

  if ( lupapisteApp.models.currentUser.isAuthority() ) {
    self.handlersDataProvider = new LUPAPISTE.HandlersDataProvider();
    self.organizationsFilterDataProvider = new LUPAPISTE.OrganizationsFilterDataProvider(self.dataProvider.applicationOrganizations);
    // TODO just search single organization tags for now, later do some grouping stuff in autocomplete component
    self.organizationTagsDataProvider = new LUPAPISTE.OrganizationTagsDataProvider(self.dataProvider.applicationTags);
  }
};

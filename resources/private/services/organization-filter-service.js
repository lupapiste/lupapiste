LUPAPISTE.OrganizationFilterService = function() {
  "use strict";
  var self = this;

  self.selected = ko.observableArray([]);

  var defaultFilter = ko.pureComputed(function() {
    var applicationFilters = _.first(lupapisteApp.models.currentUser.applicationFilters());
    return applicationFilters &&
           applicationFilters.filter.organizations &&
           applicationFilters.filter.organizations() ||
           [];
  });

  var organizationNames = ko.observable();

  // calculate data based on organizations for user and organization names from backend
  self.data = ko.pureComputed(function() {
    var usersOwnOrganizations = _.keys(lupapisteApp.models.currentUser.orgAuthz());
    var ownOrgsWithNames = _.map(usersOwnOrganizations, function(org) {
      var name = util.getIn(organizationNames(), ["names", org, loc.currentLanguage]);
      var fallback = util.getIn(organizationNames(), ["names", org, "fi"]); // fallback to "fi" language
      return {id: org, label: name ? name : fallback};
    });

    return ownOrgsWithNames;
  });

  // add default filter items when data or filter updates
  ko.computed(function() {
    ko.utils.arrayPushAll(self.selected,
      _.filter(self.data(), function(org) {
        return _.contains(defaultFilter(), org.id);
      }));
  });

  // TODO maybe get organizations with names with a single query, now we depend on two separate
  // async updates (get-organization-names command and currentUser.orgAuthz)
  ajax
    .query("get-organization-names")
    .error(_.noop)
    .success(function(res) {
      organizationNames(res);
    })
    .call();
};

LUPAPISTE.OrganizationFilterService = function(applicationFiltersService) {
  "use strict";
  var self = this;

  self.selected = ko.observableArray([]);

  var defaultFilter = ko.pureComputed(function() {
    var applicationFilters = _.find(applicationFiltersService.savedFilters(), function(f) {
      return f.isDefaultFilter();
    });
    return util.getIn(applicationFilters, ["filter", "organizations"]) || [];
  });

  var savedFilter = ko.pureComputed(function() {
    return util.getIn(applicationFiltersService.selected(), ["filter", "organizations"]);
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
    self.selected([]);
    ko.utils.arrayPushAll(self.selected,
      _.filter(self.data(), function(org) {
        if (savedFilter()) {
          return  _.contains(savedFilter(), org.id);
        } else {
          return _.contains(defaultFilter(), org.id);
        }
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

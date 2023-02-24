LUPAPISTE.OrganizationFilterService = function(applicationFiltersService) {
  "use strict";
  var self = this;

  self.selected = ko.observableArray([]);

  var savedFilter = ko.pureComputed(function() {
    return util.getIn(applicationFiltersService.selected(), ["filter", "organizations"]);
  });

  var organizationNames = ko.observable();

  organizationNames.subscribe( function() {
    hub.send( "organizationFilterService::changed",{});
  });

  // calculate data based on organizations for user and organization names from backend
  self.data = ko.pureComputed(function() {
    var usersOwnOrganizations = _.keys(lupapisteApp.models.currentUser.orgAuthz());
    var ownOrgsWithNames = _.map(usersOwnOrganizations, function(org) {
      var name = util.getIn(organizationNames(), ["names", org, loc.currentLanguage]);
      var fallback = util.getIn(organizationNames(), ["names", org, "fi"]) || org; // fallback to "fi" language
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
          return  _.includes(savedFilter(), org.id);
        }
      }));
  });

  // TODO maybe get organizations with names with a single query, now we depend on two separate
  // async updates (get-organization-names command and currentUser.orgAuthz)
  function load() {
    if (lupapisteApp.models.globalAuthModel.ok("get-organization-names")) {
      ajax
        .query("get-organization-names")
        .success(function(res) {
          organizationNames(res);
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

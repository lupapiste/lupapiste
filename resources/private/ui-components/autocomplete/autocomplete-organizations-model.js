LUPAPISTE.AutocompleteOrganizationsModel = function(params) {
  "use strict";
  var self = this;

  self.selected = params.selectedValues;
  self.query = ko.observable("");

  var orgData = ko.observable();

  var usersOwnOrganizations = _.keys(lupapisteApp.models.currentUser.orgAuthz());

  var defaultFilter = ko.pureComputed(function() {
    var applicationFilters = lupapisteApp.models.currentUser.applicationFilters;
    return applicationFilters && 
           applicationFilters()[0].filter.organizations &&
           applicationFilters()[0].filter.organizations() ||
           [];
  });

  ajax
  .query("get-organization-names")
  .error(_.noop)
  .success(function(res) {
    var ownOrgsWithNames = _.map(usersOwnOrganizations, function(org) {
      return {id: org, label: res.names[org][loc.currentLanguage]};
    });
    orgData(ownOrgsWithNames);


    ko.utils.arrayPushAll(self.selected,
      _.filter(ownOrgsWithNames, function(org) {
        return _.contains(defaultFilter(), org.id);
      }));
  })
  .call();

  self.data = ko.pureComputed(function() {
    var q = self.query() || "";
    return _.filter(orgData(), function(item) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(item.label.toUpperCase(), word.toUpperCase()) &&
          !_.some(self.selected(), item) && result;
      }, true);
    });
  });
};

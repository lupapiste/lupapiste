LUPAPISTE.AutocompleteOrganizationsModel = function(params) {
  "use strict";
  var self = this;

  self.selected = params.selectedValues;
  self.query = ko.observable("");

  var orgData = ko.observable();

  var usersOwnOrganizations = _.keys(lupapisteApp.models.currentUser.orgAuthz());

  ajax
  .query("get-organization-names")
  .error(_.noop)
  .success(function(res) {
    var ownOrgsWithNames = _.map(usersOwnOrganizations, function(org) {
      return {id: org, label: res.names[org][loc.currentLanguage]};
    });
    orgData(ownOrgsWithNames);
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

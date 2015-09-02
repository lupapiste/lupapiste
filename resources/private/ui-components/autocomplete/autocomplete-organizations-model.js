LUPAPISTE.AutocompleteOrganizationsModel = function() {
  "use strict";
  var self = this;

  self.selected = lupapisteApp.services.organizationFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    var filteredData = util.filterDataByQuery(lupapisteApp.services.organizationFilterService.data(), self.query() || "", self.selected());
    return _.sortBy(filteredData, "label");
  });
};

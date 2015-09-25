LUPAPISTE.AutocompleteOrganizationsModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;

  self.selected = lupapisteApp.services.organizationFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    var filteredData = util.filterDataByQuery(lupapisteApp.services.organizationFilterService.data(), self.query() || "", self.selected());
    return _.sortBy(filteredData, "label");
  });
};

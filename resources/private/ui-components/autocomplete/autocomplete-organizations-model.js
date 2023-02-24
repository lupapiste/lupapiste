LUPAPISTE.AutocompleteOrganizationsModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;
  self.disable = params.disable || false;

  self.selected = lupapisteApp.services.organizationFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    var filteredData = util.filterDataByQuery({data: lupapisteApp.services.organizationFilterService.data(),
                                               query: self.query(),
                                               selected: self.selected()});
    return _.sortBy(filteredData, "label");
  });
};

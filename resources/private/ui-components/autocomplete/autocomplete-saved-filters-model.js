LUPAPISTE.AutocompleteSavedFiltersModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;

  self.selected = lupapisteApp.services.applicationFiltersService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    if (params.foreman) {
      return util.filterDataByQuery(lupapisteApp.services.applicationFiltersService.savedForemanFilters(), self.query() || "", self.selected(), "title");
    }
    return util.filterDataByQuery(lupapisteApp.services.applicationFiltersService.savedFilters(), self.query() || "", self.selected(), "title");
  });
};

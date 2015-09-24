LUPAPISTE.AutocompleteSavedFiltersModel = function() {
  "use strict";
  var self = this;

  self.selected = lupapisteApp.services.applicationFiltersService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    return util.filterDataByQuery(lupapisteApp.services.applicationFiltersService.savedFilters(), self.query() || "", self.selected(), "title");
  });
};

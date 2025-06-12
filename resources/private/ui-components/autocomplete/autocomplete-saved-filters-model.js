LUPAPISTE.AutocompleteSavedFiltersModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;

  self.selected = lupapisteApp.services.applicationFiltersService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    return util.filterDataByQuery({data: params.savedFilters ? params.savedFilters() : lupapisteApp.services.applicationFiltersService.savedFilters(),
                                     query: self.query(),
                                     selected: self.selected(),
                                     label: "title"});
  });
};

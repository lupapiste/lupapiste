LUPAPISTE.AutocompleteSavedFiltersModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;

  self.selected = lupapisteApp.services.applicationFiltersService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    if (params.foreman) {
      return util.filterDataByQuery({data: lupapisteApp.services.applicationFiltersService.savedForemanFilters(),
                                     query: self.query(),
                                     selected: self.selected(),
                                     label: "title"});
    }
    return util.filterDataByQuery({data: lupapisteApp.services.applicationFiltersService.savedFilters(),
                                   query: self.query(),
                                   selected: self.selected(),
                                   label: "title"});
  });
};

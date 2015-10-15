LUPAPISTE.AutocompleteHandlersModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;

  self.selected = lupapisteApp.services.handlerFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    return _(util.filterDataByQuery({data: lupapisteApp.services.handlerFilterService.data(),
                                     query: self.query(),
                                     selected: self.selected(),
                                     label: "fullName"}))
      .sortBy("fullName")
      .unshift(lupapisteApp.services.handlerFilterService.noAuthority)
      .unshift(lupapisteApp.services.handlerFilterService.all)
      .value();
  });
};

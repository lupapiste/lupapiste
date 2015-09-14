LUPAPISTE.AutocompleteHandlersModel = function() {
  "use strict";
  var self = this;

  self.selected = lupapisteApp.services.handlerFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    return _(util.filterDataByQuery(lupapisteApp.services.handlerFilterService.data(), self.query() || "", self.selected(), "fullName"))
      .sortBy("label")
      .unshift({id: "no-authority", fullName: "Ei käsittelijää", behaviour: "singleSelection"})
      .unshift({id: "all", fullName: "Kaikki", behaviour: "clearSelected"})
      .value();
  });
};

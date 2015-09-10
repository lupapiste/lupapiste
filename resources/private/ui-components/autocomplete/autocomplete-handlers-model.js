LUPAPISTE.AutocompleteHandlersModel = function() {
  "use strict";
  var self = this;

  self.selected = lupapisteApp.services.handlerFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    var q = self.query() || "";
    return _(lupapisteApp.services.handlerFilterService.data())
      .filter(function(item) {
        return _.reduce(q.split(" "), function(result, word) {
          return _.contains(item.fullName.toUpperCase(), word.toUpperCase()) && result;
        }, true);
      })
      .sortBy("label")
      .unshift({id: "no-authority", fullName: "no-authority"})
      .value();
  });
};

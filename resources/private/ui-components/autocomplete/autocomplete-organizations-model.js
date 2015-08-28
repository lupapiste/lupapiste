LUPAPISTE.AutocompleteOrganizationsModel = function() {
  "use strict";
  var self = this;

  self.selected = lupapisteApp.services.organizationFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    var q = self.query() || "";
    return _.filter(lupapisteApp.services.organizationFilterService.data(), function(item) {
      return _.reduce(q.split(" "), function(result, word) {
        return _.contains(item.label.toUpperCase(), word.toUpperCase()) &&
          !_.some(self.selected(), item) && result;
      }, true);
    });
  });
};

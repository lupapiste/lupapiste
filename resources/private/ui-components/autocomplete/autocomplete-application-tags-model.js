LUPAPISTE.AutocompleteApplicationTagsModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;
  self.selected = params.selectedValues;
  self.disable = params.disable || false;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    var organizationTags = lupapisteApp.services.organizationTagsService.currentApplicationOrganizationTags();

    var q = self.query() || "";
    return _(organizationTags)
      .filter(function(tag) {
        return _.reduce(q.split(" "), function(result, word) {
          return !_.some(self.selected(), tag) && tag.label && _.includes(tag.label.toUpperCase(), word.toUpperCase()) && result;
        }, true);
      })
      .sortBy("label")
      .value();
  });
};

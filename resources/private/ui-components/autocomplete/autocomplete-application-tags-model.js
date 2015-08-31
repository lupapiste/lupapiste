LUPAPISTE.AutocompleteApplicationTagsModel = function(params) {
  "use strict";
  var self = this;

  self.selected = params.selectedValues;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    var organizationTags = lupapisteApp.services.organizationTagsService.currentApplicationOrganizationTags();

    var filteredData = _.filter(organizationTags, function(tag) {
      return !_.some(self.selected(), tag);
    });
    var q = self.query() || "";
    filteredData = _.filter(filteredData, function(tag) {
      return _.reduce(q.split(" "), function(result, word) {
        return tag.label && _.contains(tag.label.toUpperCase(), word.toUpperCase()) && result;
      }, true);
    });
    return filteredData;
  });

};

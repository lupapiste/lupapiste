LUPAPISTE.AutocompleteApplicationCompanyTagsModel = function(params) {
  "use strict";
  var self = this;

  var selectedIds = params.selectedValues;

  var companyTags = ko.observableArray();

  self.lPlaceholder = params.lPlaceholder;
  self.disable = params.disable || false;
  self.selected = ko.pureComputed(function() {
    return _.filter(companyTags(), function(tag) {
      return _.includes(selectedIds(), tag.id);
    });
  });
  self.selected.remove = function(item) {  selectedIds.remove(item.id); };
  self.selected.push = function(item) { selectedIds.push(item.id); };

  self.query = ko.observable("");

  var companyId = util.getIn(lupapisteApp.models.currentUser, ["company","id"]);

  ajax.query("company-tags", {company: companyId})
    .success(function(resp) {
      companyTags(resp.tags);
    })
    .call();

  self.data = ko.pureComputed(function() {
    var q = self.query() || "";
    return _(companyTags())
      .filter(function(tag) {
        return _.reduce(q.split(" "), function(result, word) {
          return !_.some(self.selected(), tag) && tag.label && _.includes(tag.label.toUpperCase(), word.toUpperCase()) && result;
        }, true);
      })
      .sortBy("label")
      .value();
  });
};

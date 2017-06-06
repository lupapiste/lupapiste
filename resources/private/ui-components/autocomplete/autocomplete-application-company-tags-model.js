LUPAPISTE.AutocompleteApplicationCompanyTagsModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;
  self.selected = params.selectedValues;
  self.disable = params.disable || false;

  self.query = ko.observable("");

  var companyTags = ko.observable();

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

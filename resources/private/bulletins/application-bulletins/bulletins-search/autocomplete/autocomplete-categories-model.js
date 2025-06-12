LUPAPISTE.AutocompleteCategoriesModel = function(params) {
  "use strict";
  var self = this;

  self.label = params.label;
  self.selected = params.selected;
  self.query = params.query;
  self.queryString = ko.observable("");

  self.data = ko.pureComputed(function() {
    // TODO refactor clearSelected item into autocomplete
    var all = {id: "all", label: loc("bulletin.categories-search.caption"), behaviour: "clearSelected"};
    var categoryData = _.map(params.categories(), function(m) {
      return {id: m, label: loc(["bulletin", "category", m])};
    });
    return _(util.filterDataByQuery({data: categoryData,
                                     query: self.queryString(),
                                     selected: self.selected()}))
      .sortBy("label")
      .unshift(all)
      .value();
  });
};

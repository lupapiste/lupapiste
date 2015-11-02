LUPAPISTE.AutocompleteStatesModel = function(params) {
  "use strict";
  var self = this;

  self.selected = params.selected;
  self.query = params.query;
  self.queryString = ko.observable("");

  self.data = ko.pureComputed(function() {
    // TODO refactor clearSelected item into autocomplete
    var all = {id: "all", label: loc("bulletin.states-search.caption"), behaviour: "clearSelected"};
    var stateData = _.map(params.states(), function(m) {
      return {id: m, label: loc(["bulletin", "state", m])};
    });
    return _(util.filterDataByQuery({data: stateData,
                                     query: self.queryString(),
                                     selected: self.selected()}))
      .sortBy("label")
      .unshift(all)
      .value();
  });
};
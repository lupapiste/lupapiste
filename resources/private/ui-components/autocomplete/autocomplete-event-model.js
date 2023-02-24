LUPAPISTE.AutocompleteEventModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;
  self.disable = params.disable || false;

  self.selected = lupapisteApp.services.eventFilterService.selected;

  self.query = ko.observable("");

  function wrapInObject(event) {
    return {
      label: loc("applications.event." + event),
      id: event,
      behaviour: "singleSelection"};
   }

  self.data = ko.pureComputed(function() {
    var events = _.map(lupapisteApp.services.eventFilterService.data(), function(event) {
      return wrapInObject(event);
    });
    var filteredData = util.filterDataByQuery({data: events,
      query: self.query(),
      selected: self.selected()});
    return _.sortBy(filteredData, "label");
  });
};


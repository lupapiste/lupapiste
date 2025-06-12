LUPAPISTE.TagFilterService = function(tagsService, applicationFiltersService, filterKey) {
  "use strict";
  var self = this;

  self.data = tagsService.data;

  self.selected = ko.observableArray([]);

  var savedFilter = ko.pureComputed(function() {
    return util.getIn(applicationFiltersService.selected(), ["filter", filterKey]);
  });

  ko.computed(function() {
    self.selected([]);
    ko.utils.arrayPushAll(self.selected,
                          _(self.data())
                          .map("tags")
                          .flatten()
                          .filter(function(tag) {
                            if (tag && savedFilter()) {
                              return  _.includes(savedFilter(), tag.id);
                            }
                          })
                          .value());
  });
};

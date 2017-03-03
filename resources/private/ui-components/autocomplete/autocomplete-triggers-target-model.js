LUPAPISTE.AutocompleteTriggersTargetModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.triggersTargetService;

  self.selected = service.selected;
  self.query = ko.observable("");

  function buildTitle(type) {
    return loc(["attachmentType", type["type-group"], type["type-id"]].join("."));
  }

  var attachmentTypes = self.disposedPureComputed(function() {
    return _.map(service.data(), function(type) {
      return _.set(type, "title", buildTitle(type));
    });
  });

  function typeGroupIs(group, type) {
    return type["type-group"] === group;
  }

  function buildGroupTitle(typeGroup) {
    return loc(["attachmentType", typeGroup, "_group_label"].join("."));
  }

  function withGroupHeaders(data) {
    return _(data)
      .map("type-group")
      .uniq()
      .map(function(typeGroup) {
        var header = {"type-group": typeGroup, "title": buildGroupTitle(typeGroup), groupHeader: true};
        var types = _.filter(data, _.partial(typeGroupIs, typeGroup));
        return _.concat(header, types);
      })
      .flatten()
      .value();
  }

  self.data = self.disposedPureComputed(function() {
    return withGroupHeaders(util.filterDataByQuery({data: attachmentTypes(),
      query: self.query(),
      selected: self.selected(),
      label: "title"}));
  });
};

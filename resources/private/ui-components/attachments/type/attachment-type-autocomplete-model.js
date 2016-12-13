LUPAPISTE.AttachmentTypeAutocompleteModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;

  self.selected = params.selectedType;

  function buildTitle(type) {
    return loc(["attachmentType", type["type-group"], type["type-id"]].join("."));
  }

  var attachmentTypes = self.disposedPureComputed(function() {
    return _.map(service.attachmentTypes(), function(type) {
      return _.set(type, "title", buildTitle(type));
    });
  });

  function typeGroupIs(group, type) {
    return type["type-group"] === group;
  }

  var options = self.disposedPureComputed(function() {
    if (ko.unwrap(params.selectedTypeGroup)) {
      return _.filter(attachmentTypes(), _.partial(typeGroupIs, params.selectedTypeGroup().value));
    } else {
      return attachmentTypes();
    }
  });

  self.query = ko.observable("");

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
    return withGroupHeaders(util.filterDataByQuery({data: options(),
                                                    query: self.query(),
                                                    selected: self.selected(),
                                                    label: "title"}));
  });
};

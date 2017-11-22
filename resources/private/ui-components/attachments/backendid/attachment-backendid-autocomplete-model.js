LUPAPISTE.AttachmentBackendidAutocompleteModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.selectedId = ko.observable(params.selected);
  self.disabled = params.disabled;

  var service = lupapisteApp.services.attachmentsService;

  var options = self.disposedPureComputed(function() {
    return _.map(service.attachmentBackendIds(), function(id) {
      return {"value": id, "title": id};
    });
  });

  self.query = ko.observable("");

  self.data = self.disposedPureComputed(function() {
    return util.filterDataByQuery({data: options(),
      query: self.query(),
      selected: self.selectedId(),
      label: "title"});
  });

  self.selected = self.disposedPureComputed({
    read: function() {
      return {"value": self.selectedId(), "title": self.selectedId()};
    },
    write: function(selected) {
      self.selectedId(selected["value"]);
    }
  });
};

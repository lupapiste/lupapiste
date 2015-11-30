LUPAPISTE.EditableBulletinModel = function(data, id) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  console.log("data", id);

  var mapping = {
    copy: ["bulletinState"],
    proclamationStartsAt: {
      create: function(obj) {
        return ko.observable(new Date(obj.data));
      }
    },
    proclamationEndsAt: {
      create: function(obj) {
        return ko.observable(new Date(obj.data));
      }
    },
    proclamationText: {
      create: function(obj) {
        return ko.observable(obj.data);
      }
    }
  };

  ko.mapping.fromJS(data, mapping, this);

  self.isValid = ko.observable(true);

  self.pending = ko.observable(false);

  self.edit = ko.observable(false);

  self.editable = ko.observable(false);

  self.save = function() {
    self.sendEvent("publishBulletinService", "saveProclaimedBulletin", {
      bulletinId: id,
      bulletinVersionId: data.id,
      proclamationEndsAt: self.proclamationEndsAt().getTime(),
      proclamationStartsAt: self.proclamationStartsAt().getTime(),
      proclamationText: self.proclamationText()
    });
  }
}

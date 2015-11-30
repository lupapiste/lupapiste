LUPAPISTE.EditableProclaimedBulletinModel = function(data, id) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.EditableBulletinModel());

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

  ko.mapping.fromJS(data, mapping, self);

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

LUPAPISTE.EditableVerdictGivenBulletinModel = function(data, id) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.EditableBulletinModel());

  var mapping = {
    copy: ["bulletinState"],
    verdictGivenAt: {
      create: function(obj) {
        return ko.observable(new Date(obj.data));
      }
    },
    appealPeriodStartsAt: {
      create: function(obj) {
        return ko.observable(new Date(obj.data));
      }
    },
    appealPeriodEndsAt: {
      create: function(obj) {
        return ko.observable(new Date(obj.data));
      }
    },
    verdictGivenText: {
      create: function(obj) {
        return ko.observable(obj.data);
      }
    }
  };

  ko.mapping.fromJS(data, mapping, self);

  self.save = function() {
    self.sendEvent("publishBulletinService", "saveVerdictGivenBulletin", {
      bulletinId: id,
      bulletinVersionId: data.id,
      verdictGivenAt:       self.verdictGivenAt().getTime(),
      appealPeriodStartsAt: self.appealPeriodStartsAt().getTime(),
      appealPeriodEndsAt:   self.appealPeriodEndsAt().getTime(),
      verdictGivenText:     self.verdictGivenText() || ""
    });
  }
}

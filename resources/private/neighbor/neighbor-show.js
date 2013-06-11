;(function() {
  "use strict";

  function Model() {
    var self = this;

    self.map = null;

    self.init = function(e) {
      var a = e.pagePath[0],
          n = e.pagePath[1],
          t = e.pagePath[2];
      if (!a || !n || !t) { window.location.hash = "!/404"; }
      self
        .pending(false)
        .application(null)
        .applicationId(a)
        .neighborId(n)
        .token(t)
        .response(null)
        .message("")
        .saving(false)
        .done(false)
        .fetch();
      return self;
    };

    self.fetch = function() {
      ajax
        .query("neighbor-application")
        .param("applicationId", self.applicationId())
        .param("neighborId", self.neighborId())
        .param("token", self.token())
        .success(self.success)
        .fail(self.fail)
        .error(self.error)
        .pending(self.pending)
        .call();
    };

    self.success = function(data) {
      var a = data.application,
          l = a.location,
          x = l.x,
          y = l.y;
      self.application(a).map.updateSize().clear().center(x, y, 12).add(x, y);
      docgen.displayDocuments("#neighborDocgen", a, _.filter(a.documents, null, function(doc) {return doc.schema.info.type !== "party"; }));
      docgen.displayDocuments("#neighborPartiesDocgen",     a, _.filter(a.documents, null, function(doc) {return doc.schema.info.type === "party"; }));
      self.attachmentsByGroup(getAttachmentsByGroup(a.attachments));
      self.attachments(_.map(a.attachments || [], function(a) {
        a.latestVersion = _.last(a.versions);
        return a;
      }));
      return self;
    };

    self.fail = function(data) {
      // TODO: Show information about application not found, or closed, or sumthing.
      return self;
    };

    self.error = function(data) {
      // TODO: Show error page
      return self;
    };

    self.attachments = ko.observableArray([]);
    self.attachmentsByGroup = ko.observableArray();
    self.pending = ko.observable();
    self.neighborId = ko.observable();
    self.token = ko.observable();
    self.applicationId = ko.observable();
    self.application = ko.observable();
    self.saving = ko.observable();
    self.done = ko.observable();
    self.responses = [{text: loc("neighbor.show.response.approve"), value: "approve"},
                      {text: loc("neighbor.show.response.disapprove"), value: "disapprove"}];
    self.response = ko.observable();
    self.message = ko.observable();
    self.operations = ko.observable();
    self.operationsCount = ko.observable();
    self.messageEnabled = ko.computed(function() { var c = self.response(); return c && c.value === "disapprove"; });
    
    self.send = function() {
      ajax
        .command("neighbor-response", {
          applicationId: self.applicationId(),
          neighborId: self.neighborId(),
          token: self.token(),
          response: self.response().value,
          message: self.message()
        })
        .pending(self.saving)
        .success(self.sendOk)
        .fail(self.fail)
        .error(self.error)
        .call();
    };

    self.sendOk = function() {
      self.done(true);
      // TODO: ... now what?
    };
  }

  function getAttachmentsByGroup(source) {
    var attachments = _.map(source, function(a) { a.latestVersion = _.last(a.versions || []); return a; });
    var grouped = _.groupBy(attachments, function(attachment) { return attachment.type['type-group']; });
    return _.map(grouped, function(attachments, group) { return {group: group, attachments: attachments}; });
  }
  var model = new Model();

  hub.onPageChange("neighbor-show", model.init);

  $(function() {
    model.map = gis.makeMap("neighbor-map", false).updateSize().center(404168, 6693765, 12);
    $("#neighbor-show").applyBindings(model);
  });

})();

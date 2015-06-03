;(function() {
  "use strict";

  function Model() {
    var self = this;

    self.inError = ko.observable(false);
    self.errorText = ko.observable("");
    self.error = function(data) {
      self.inError(true);
      self.errorText(data.text);
    };

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
        .error(self.error)
        .pending(self.pending)
        .call();
    };

    self.success = function(data) {
      var a = data.application,
          l = a.location,
          x = l.x,
          y = l.y;

      self.application(a).map.updateSize().clear().center(x, y, 14).add({x: x, y: y});

      var nonpartyDocs = _.filter(a.documents, util.isNotPartyDoc);
      var sortedNonpartyDocs = _.sortBy(nonpartyDocs, util.getDocumentOrder);
      var partyDocs = _.filter(a.documents, util.isPartyDoc);
      var sortedPartyDocs = _.sortBy(partyDocs, util.getDocumentOrder);
      var options = {disabled: true, validate: false};

      docgen.displayDocuments("#neighborDocgen", a, sortedNonpartyDocs, lupapisteApp.models.applicationAuthModel, options);
      docgen.displayDocuments("#neighborPartiesDocgen", a, sortedPartyDocs, lupapisteApp.models.applicationAuthModel, options);

      self.attachmentsByGroup(getAttachmentsByGroup(a.attachments));
      self.attachments(_.map(a.attachments || [], function(a) {
        a.latestVersion = _.last(a.versions);
        return a;
      }));

      self.primaryOperation(a.primaryOperation);
      self.secondaryOperations(a.secondaryOperations);

      self.operationsCount(_.map(_.countBy(a.secondaryOperations, "name"), function(v, k) { return {name: k, count: v}; }));

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
    self.response = ko.observable();
    self.message = ko.observable();
    self.operationsCount = ko.observable();
    self.tupasUser = ko.observable();
    self.primaryOperation = ko.observable();
    self.secondaryOperations = ko.observableArray();

    self.primaryOperationName = ko.computed(function() {
      var op = ko.unwrap(self.primaryOperation());
      if (op) {
        return "operations." + ko.unwrap(op.name);
      }
      return "";
    });

    self.send = function() {
      ajax
        .command("neighbor-response", {
          applicationId: self.applicationId(),
          neighborId: self.neighborId(),
          token: self.token(),
          stamp: self.tupasUser().stamp,
          response: self.response(),
          message: self.message()
        })
        .pending(self.saving)
        .success(function() {
          self.done(true);
        })
        .call();
    };
  }

  function getAttachmentsByGroup(source) {
    var attachments = _.map(source, function(a) { a.latestVersion = _.last(a.versions || []); return a; });
    var grouped = _.groupBy(attachments, function(attachment) { return attachment.type["type-group"]; });
    return _.map(grouped, function(attachments, group) { return {group: group, attachments: attachments}; });
  }
  var model = new Model();

  hub.onPageLoad("neighbor-show", function(e) {
    model.init(e);
    vetuma($("#vetuma-neighbor"), function(user) {
      model.tupasUser(user);
      $("html, body").animate({ scrollTop: 10000});
    });
  });

  $(function() {
    model.map = gis
      .makeMap("neighbor-map", false)
      .updateSize()
      .center(404168, 6693765, 14);
    $("#neighbor-show").applyBindings(model);
  });

})();

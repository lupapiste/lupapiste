;(function($) {
  "use strict";

  var url = window.location.pathname + window.location.search;

  var VetumaButtonModel = function() {
    var self = this;
    self.id = "vetuma-init";
    self.success = url;
    self.cancel  = url + "#cancel";
    self.error   = url + "#error";
    self.y       = url + "#y";
    self.vtj     = url + "#vtj";
    self.visible = ko.observable(true);
  };

  var vetumaParams = new VetumaButtonModel();

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
      if (!a || !n || !t) {
        pageutil.openPage("404");
      }
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
      var options = {disabled: true, validate: false, authorizationModel: lupapisteApp.models.applicationAuthModel};

      docgen.displayDocuments("neighborDocgen", a, sortedNonpartyDocs, options);
      docgen.displayDocuments("neighborPartiesDocgen", a, sortedPartyDocs, options);

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

    self.primaryOperationName = ko.pureComputed(function() {
      var opName = util.getIn(self.primaryOperation, ["name"]);
      return !_.isEmpty(opName) ? "operations." + opName : "";
    });

    self.send = function() {
      ajax
        .command("neighbor-response", {
          applicationId: self.applicationId(),
          neighborId: self.neighborId(),
          token: self.token(),
          stamp: self.tupasUser().stamp,
          lang: loc.getCurrentLanguage(),
          response: self.response(),
          message: self.message()
        })
        .pending(self.saving)
        .success(function() {
          self.done(true);
        })
        .call();
    };

    self.status = ko.observable();
  }

  function getAttachmentsByGroup(source) {
    var attachments = _.map(source, function(a) { a.latestVersion = _.last(a.versions || []); return a; });
    var grouped = _.groupBy(attachments, function(attachment) { return attachment.type["type-group"]; });
    return _.map(grouped, function(attachments, group) { return {group: group, attachments: attachments}; });
  }

  var model = new Model();
  model.vetuma = vetumaParams;

  function scrollToResponse() {
    document.getElementById("responseHeading").scrollIntoView();
    window.scrollBy(0, -60); // header blocks the view
  }

  function gotUser(user) {
    vetumaParams.visible(false);
    model.tupasUser(user);
    _.delay( function() {
      window.scrollTo( 0 , document.body.scrollHeight);
    }, 1000);
  }

  hub.onPageLoad("neighbor-show", function(e) {
    var hash = window.location.hash.replace("#","");
    model.init(e);
    if (hash) {
      model.status(hash);
      scrollToResponse();
    } else {
      vetuma.getUser(gotUser, _.noop);
    }
  });

  $(function() {
    model.map = gis
      .makeMap("neighbor-map", {zoomWheelEnabled: false})
      .updateSize()
      .center(404168, 6693765, 14);
    $("#neighbor-show").applyBindings(model);
  });

})(jQuery);

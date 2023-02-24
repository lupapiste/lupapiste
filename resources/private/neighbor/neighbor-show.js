;(function($) {
  "use strict";

  var VetumaButtonModel = function() {
    var self = this;
    var statuses = ["cancel", "error", "y", "vtj"];
    var hash = window.location.hash;
    if (_.includes(statuses, _.last(_.split(hash, "/")))) {
      // build hash without the last 'status' part - we don't want to concatenate status again, eg "/cancel/cancel"
      // _.initial "Gets all but the last element of array."
      hash = _(hash).split("/").initial().join("/");
    }
    var url = window.location.pathname + "/" + hash;
    self.id = "vetuma-init";
    self.success = url;
    self.cancel  = url + "/cancel";
    self.error   = url + "/error";
    self.y       = url + "/y";
    self.vtj     = url + "/vtj";
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

      var nonpartyDocs = _.reject(a.documents, util.isPartyDoc);
      var sortedNonpartyDocs = _.sortBy(nonpartyDocs, util.getDocumentOrder);
      var partyDocs = _.filter(a.documents, util.isPartyDoc);
      var sortedPartyDocs = _.sortBy(partyDocs, util.getDocumentOrder);
      var options = {disabled: true, validate: false, authorizationModel: lupapisteApp.models.applicationAuthModel};

      docgen.displayDocuments("neighborDocgen", a, sortedNonpartyDocs, options);
      docgen.displayDocuments("neighborPartiesDocgen", a, sortedPartyDocs, options);

      self.attachmentsByGroup(getAttachmentsByGroup(a.attachments));
      self.attachments(a.attachments);

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

    self.fileDownloadLink = function(attachment) {
      return "/api/raw/neighbor-download-attachment?neighborId="
             + self.neighborId()
             + "&token=" + self.token()
             + "&fileId=" + attachment.latestVersion.fileId
             + "&applicationId=" + self.applicationId();
    };

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

  function getAttachmentsByGroup(attachments) {
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

  hub.onPageLoad("neighbor", function(e) {
    model.init(e);
    if (e.pagePath[3]) {
      // Hash contains some response status information from Vetuma IDP
      model.status(e.pagePath[3]);
      _.delay(scrollToResponse, 1000);
    } else {
      vetuma.getUser(gotUser, _.noop, gotUser);
    }
  });

  $(function() {
    model.map = gis
      .makeMap("neighbor-map", {zoomWheelEnabled: false})
      .updateSize()
      .center(404168, 6693765, 14);
    $("#neighbor").applyBindings(model);
  });

})(jQuery);

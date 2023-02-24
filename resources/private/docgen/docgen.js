var docgen = (function () {
  "use strict";

  var docModels = {};

  function clear(containerId) {
    var containerSelector = "#" + containerId;
    var oldModels = docModels[containerId] ||  [];

    _.each($(".sticky", containerSelector), function(elem) {
      window.Stickyfill.remove(elem);
    });

    while (oldModels.length > 0) {
      oldModels.pop().dispose();
    }
    docModels[containerId] = [];

    $(containerSelector).empty();
  }

  function displayElement(input) {
    var idOfElemToReact = input.attr("path-for-display-when");
    var elemToReact = $("#" + idOfElemToReact);

    function getValOfElem() {
      var notFound = null;
      var elemType = elemToReact.length ? elemToReact[0].type : notFound;

      switch( elemType ) {
        case "checkbox":
          return elemToReact[0].checked.toString();
        case "radioGroup":
          var radioButton = elemToReact[0].querySelector("input[type=radio]:checked");
          return radioButton ? radioButton.value : notFound;
        case notFound:
          return notFound;
        default:
          return elemToReact[0].value;
      }
    }

    function displayOrHide(elemVal) {
      var mode = input.attr( "mode-for-display-when" );
      var valuesToReact = input.attr("values-for-display-when").split("||,");

      var matches = _.find(valuesToReact, function(val) {
        return val === elemVal;
      });
      var display = "none";

      if ((mode === "show" && matches ) || (mode === "hide" && !matches)) {
        display = ""; // Use inherited display style
      }

      // 1) Using both style attribute (.css() call) and class are partially redundant in most cases.
      // Here both are used just in case and to minimize need for additional testing.
      // In _forms.scss there is a class that sets "display: block !imporant" and effectively
      // overrides element style. In element's style use of important is not allowed. Now
      // class is used for those cases for which css style does not work.
      // 2) it's possible that form element is wrapped into a div (e.g. checkbox). Hence, .closest() should be used
      // instead of .parent().
      input.closest(".form-entry,.form-group,.form-table,.button-group").css("display", display);
      if(display === "none") {
        input.closest(".form-entry,.form-group,.form-table,.button-group").addClass("hidden-conditionally");
      } else {
        input.closest(".form-entry,.form-group,.form-table,.button-group").removeClass("hidden-conditionally");
      }
      hub.send( "visible-elements-changed" );
    }

    function onChangeDisplayOrHide(e) {
      displayOrHide(getValOfElem(e.target));
    }

    displayOrHide(getValOfElem());
    $(elemToReact.on("change",onChangeDisplayOrHide));
  }
  function initDisplayWhen(contextNode, i, e) {
    displayElement($(e), contextNode);
  }
  function initDisplayWhenFor(selector, contextNode) {
    $(selector, contextNode).each(initDisplayWhen.bind(null, contextNode));
  }

  function displayDocuments(containerId, application, documents, options) {
    var docgenDiv = $("#" + containerId);
    var isDisabled = options && options.disabled;

    function updateOther(select) {
      var otherId = select.attr("data-input-other-id"),
          other = $("#" + otherId, select.parent().parent());
      if (select.context && select.context.type === "checkbox") {
        other.parent().css("visibility", select.context.checked === true ? "visible" : "hidden");
      }
      else {
        other.parent().css("visibility", select.val() === "other" ? "visible" : "hidden");
      }
    }

    function initSelectWithOther(i, e) { updateOther($(e)); }
    function selectWithOtherChanged(event) {
      updateOther($(event.target));
    }

    clear(containerId);

    _.each(documents, function (doc) {
      var schema = doc.schema;
      // Use given authorization model or create new one from current document
      if (!options.authorizationModel && _.isEmpty(doc.allowedActions)) {
        error("No authorization model, form will be disabled!", containerId, doc.id);
      }
      var authorizationModel = options.authorizationModel ? options.authorizationModel : authorization.create(doc.allowedActions);

      var docModel = new DocModel(schema, doc, application, authorizationModel, options);
      docModels[containerId].push(docModel);
      docModel.triggerEvents();

      docgenDiv.append(docModel.element);

      if (schema.info.repeating && !schema.info["no-repeat-button"] && !isDisabled && authorizationModel.ok("create-doc")) {
        var icon = $("<i>", {"class": "lupicon-circle-plus"});
        var span = $("<span>").text( loc(schema.info.name + "._append_label"));
        var btn = $("<button>",
                    {"data-test-id": schema.info.name + "_append_btn", "class": "secondary"})
          .on("click", function () {
            var self = this;
            ajax
              .command("create-doc", { schemaName: schema.info.name, id: application.id, collection: docModel.getCollection() })
              .success(function (resp) {
                var newDocId = resp.doc;
                var newDocSchema = _.cloneDeep(schema);
                newDocSchema.info.op = null;
                var newAuthModel = authorizationModel.clone();
                authorization.refreshModelsForCategory( _.set( _.set({}, doc.id, authorizationModel),
                                                               newDocId,
                                                               newAuthModel ),
                                                        application.id,
                                                        "documents");
                // The new document might contain some default values, get them from backend
                ajax.query("document", {id: application.id, doc: newDocId, collection: docModel.getCollection()})
                  .success(function(data) {
                    var newDoc = data.document;
                    newDoc.schema = newDocSchema;
                    lupapisteApp.services.accordionService.addDocument(newDoc);

                    var newDocModel = new DocModel(newDocSchema, newDoc, application, newAuthModel, options);
                    newDocModel.triggerEvents();

                    $(self).before(newDocModel.element);
                    $(".sticky", newDocModel.element).Stickyfill();
                  })
                  .call();
              })
              .call();
          });
        btn.append( [icon, span]);
        docgenDiv.append(btn);
      }
    });

    $("select[data-input-other-id]", docgenDiv).each(initSelectWithOther).on("change",selectWithOtherChanged);
    $(".sticky", docgenDiv).Stickyfill();
    window.Stickyfill.rebuild();

    initDisplayWhenFor("[path-for-display-when]", docgenDiv);
  }


  function nonApprovedDocuments() {
    var models = _.concat.apply(null, _.values(docModels));
    return _.filter(models, function(docModel) {
      return !docModel.isApproved();
    });
  }

  return {
    initDisplayWhenFor: initDisplayWhenFor,
    displayDocuments: displayDocuments,
    clear: clear,
    nonApprovedDocuments: nonApprovedDocuments
  };

})();

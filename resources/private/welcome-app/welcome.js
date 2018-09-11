;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({startPage: "login",
                                           logoPath: pageutil.frontpage,
                                           allowAnonymous: true,
                                           showUserMenu: false});
  $(lupapisteApp.domReady);

  lupapisteApp.models.globalAuthModel = authorization.create();
  lupapisteApp.models.globalAuthModel.refreshWithCallback({}, _.partial(hub.send, "global-auth-model-loaded")); // no application bound

   //ajax.query("pre-login", {parameter: "parametri"})
   //    .success( function( res ) {
   //        var whereTo =  _.get(res, "message");

   //        if (whereTo === "norm-login") {
   //            window.lupapisteApp = new LUPAPISTE.App({startPage: "login",
   //                logoPath: pageutil.frontpage,
   //                allowAnonymous: true,
   //                showUserMenu: false});
   //            $(lupapisteApp.domReady);

   //            lupapisteApp.models.globalAuthModel = authorization.create();
   //            lupapisteApp.models.globalAuthModel.refreshWithCallback({}, _.partial(hub.send, "global-auth-model-loaded")); // no application bound
   //        } else {
   //            window.open(whereTo, "_self");
   //            // window.lupapisteApp = new LUPAPISTE.App({startPage: whereTo,
   //            //     logoPath: pageutil.frontpage,
   //            //     allowAnonymous: true,
   //            //     showUserMenu: false});
   //            // $(lupapisteApp.domReady);
   //            //
   //            // lupapisteApp.models.globalAuthModel = authorization.create();
   //            // lupapisteApp.models.globalAuthModel.refreshWithCallback({}, _.partial(hub.send, "global-auth-model-loaded")); // no application bound
   //        }

   //    })
   //    .error( function( res ) {
   //        self.whereTo(  _.get(res, "message") );
   //    })
   //    .call();
   })();

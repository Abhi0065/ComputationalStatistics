     const retrying = document.getElementById("retrying");
     const h2 = document.querySelector("h2");
     const  error = document.getElementById("error");

     retrying.style.color = "#6b6b6b";
     let clearIntervalId;
     let count = 0;

     function retryingUrl(){
       clearIntervalId =  setInterval(function(){
         count++;
         retrying.innerText = `retrying connection... ${count}s`
       }, 1000);
     }

     function loadUrl() {
         window.location.replace("https://sites.google.com/view/comp-stat/home");
      }

     function showMessage(messageText, backgroundColor) {
       const message = document.getElementById("message");
       message.innerHTML = `<p>${messageText}</p>`;
       message.style.background = backgroundColor;
       message.style.top = "0";
       message.style.opacity = "1";
       setTimeout(function () {
         if (messageText !== "Offline") {
           message.style.top = "-30px";
           message.style.opacity = "0";
         } else {
           message.style.top = "0";
           message.style.opacity = "1";
         }
       }, 2500);
     }

      function online() {
        setTimeout(function (){
          loadUrl();
          clearInterval(clearIntervalId);
        },1500);
        showMessage("Back online", "green");
        h2.style.color = "green";
        h2.innerText = "Back Online";
        error.innerText = "Internet connected";
        retrying.innerText = "connection successful";
      }

      function offline() {
        showMessage("Offline", "red");
        retryingUrl();
      }

      window.addEventListener("load", function () {
        navigator.onLine ? online() : offline();
      });

      window.addEventListener("online", online);
      window.addEventListener("offline", offline);